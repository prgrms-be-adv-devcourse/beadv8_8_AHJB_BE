package com.jjinmak.payment.app.facade;

import com.jjinmak.payment.app.dto.PgReconciliationResult;
import com.jjinmak.payment.app.usecase.ReconciliationPgUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 대사 파사드. (ReconciliationScheduler 가 호출)
 *
 * 대사 1회를 세 단계로 나눠 갖는다: startPg -> reconcilePg -> finishPg. 도중에 예외가 나면 failPg.
 * 트랜잭션은 단계마다 따로 연다. reconcilePg 만 트랜잭션이 없다.
 * 토스를 여러 번 호출하는 구간이라, HTTP 를 기다리는 동안 DB 커넥션을 쥐고 있지 않게 하기 위함.
 *
 * 스케줄러가 startpg -> reconcilePg -> finishPg를 순서대로 호출.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationFacade {

    private final ReconciliationPgUseCase reconciliationPgUseCase;

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
}
