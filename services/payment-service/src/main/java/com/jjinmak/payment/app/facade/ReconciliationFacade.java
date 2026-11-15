package com.jjinmak.payment.app.facade;

import com.jjinmak.payment.app.dto.LedgerCheckResult;
import com.jjinmak.payment.app.dto.PgReconciliationResult;
import com.jjinmak.payment.app.usecase.ReconciliationLedgerUseCase;
import com.jjinmak.payment.app.usecase.ReconciliationPgUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 대사 파사드. (ReconciliationScheduler 가 호출)
 *
 * 대사 1회를 세 단계로 나눠 갖는다. 스케줄러가 순서대로 호출하고, 도중에 예외가 나면 실패 단계로 기록한다.
 *   PG 대사   : startPg -> reconcilePg -> finishPg           (실패 시 failPg)
 *   원장 대사 : startLedger -> checkLedger -> finishLedger   (실패 시 failLedger)
 *
 * 트랜잭션은 단계마다 따로 연다. 가운데 단계만 여는 방식이 다르다.
 *  - reconcilePg : 트랜잭션 없음. 토스를 여러 번 호출하는 구간이라, HTTP 를 기다리는 동안 DB 커넥션을 쥐고 있지 않게 하기 위함
 *  - checkLedger : 읽기 전용 + REPEATABLE_READ. 검사 여러 개가 같은 시점의 데이터를 보게 하기 위함.
 *                  검사 도중에 결제가 끼어들면 잔액과 원장을 서로 다른 시점으로 읽어, 실제로는 없는 불일치가 나온다
 */
@Service
@RequiredArgsConstructor
public class ReconciliationFacade {

    private final ReconciliationPgUseCase reconciliationPgUseCase;
    private final ReconciliationLedgerUseCase reconciliationLedgerUseCase;

    // PG 대사 시작. 진행할 실행의 id, 진행하지 않으면 빈 값
    @Transactional
    public Optional<Long> startPg(LocalDate targetDate) {
        return reconciliationPgUseCase.start(targetDate);
    }

    // 토스 거래와 충전 건 대조. 토스를 호출하므로 트랜잭션을 열지 않는다
    public PgReconciliationResult reconcilePg(LocalDate targetDate) {
        return reconciliationPgUseCase.reconcile(targetDate);
    }

    // 대조 결과 저장 + 실행 종료
    @Transactional
    public void finishPg(Long runId, PgReconciliationResult result) {
        reconciliationPgUseCase.finish(runId, result);
    }

    // 도중에 예외가 난 실행의 실패 기록
    @Transactional
    public void failPg(Long runId, String reason) {
        reconciliationPgUseCase.fail(runId, reason);
    }

    // 원장 대사 시작. 진행할 실행의 id, 진행하지 않으면 빈 값
    @Transactional
    public Optional<Long> startLedger(LocalDate targetDate) {
        return reconciliationLedgerUseCase.start(targetDate);
    }

    // 원장 검사. 검사들이 모두 같은 시점의 데이터를 보도록 읽기 전용 + REPEATABLE_READ 로 묶는다
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LedgerCheckResult checkLedger(LocalDate targetDate) {
        return reconciliationLedgerUseCase.check(targetDate);
    }

    // 검사 결과 저장 + 실행 종료
    @Transactional
    public void finishLedger(Long runId, LedgerCheckResult result) {
        reconciliationLedgerUseCase.finish(runId, result);
    }

    // 도중에 예외가 난 실행의 실패 기록
    @Transactional
    public void failLedger(Long runId, String reason) {
        reconciliationLedgerUseCase.fail(runId, reason);
    }
}
