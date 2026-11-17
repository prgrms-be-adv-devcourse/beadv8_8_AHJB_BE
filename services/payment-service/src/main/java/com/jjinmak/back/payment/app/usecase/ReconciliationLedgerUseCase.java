package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.back.payment.app.dto.LedgerCheckResult;
import com.jjinmak.back.payment.app.support.LedgerInvariantChecker;
import com.jjinmak.back.payment.app.support.ReconciliationRunRecorder;
import com.jjinmak.back.payment.domain.enums.ReconciliationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 원장 대사: 원장(WalletTransaction)과 잔액 · 홀딩 · 충전 상태가 서로 맞는지 확인한다. (ReconciliationScheduler → ReconciliationFacade → ReconciliationLedgerUseCase)
 *
 *   start   (트랜잭션)            실행 기록을 만들거나 다시 연다
 *   check   (읽기 전용 트랜잭션)   등록된 검사를 차례로 실행하고 결과를 합친다
 *   finish  (트랜잭션)            발견한 불일치를 저장하고 실행을 닫는다
 *   fail    (트랜잭션)            도중에 예외가 났을 때 실행을 FAILED 로 닫는다
 *
 * 검사 규칙은 LedgerInvariantChecker 구현체가 하나씩 갖는다. 여기서는 실행 순서와 결과 합치기만 맡는다.
 * 검사 하나라도 예외를 던지면 전체를 실패로 본다. 일부만 확인하고 "일치" 로 닫지 않기 위함.
 * 불일치를 발견해도 돈이나 상태를 고치지 않는다. 기록만 하고 정정은 운영자가 한다.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationLedgerUseCase {

    // 스프링이 LedgerInvariantChecker 구현체를 모두 모아서 넣어 준다
    private final List<LedgerInvariantChecker> ledgerInvariantCheckers;
    private final ReconciliationRunRecorder reconciliationRunRecorder;

    // 대사 시작. 진행할 실행의 id 를 돌려주고, 이미 끝났거나 실행 중이면 빈 값
    public Optional<Long> start(LocalDate targetDate) {
        return reconciliationRunRecorder.start(ReconciliationType.LEDGER, targetDate);
    }

    // 등록된 검사를 모두 실행한다. 저장은 하지 않고 결과만 돌려준다
    public LedgerCheckResult check(LocalDate targetDate) {
        int checkedCount = 0;
        List<DetectedMismatchDto> mismatches = new ArrayList<>();
        for (LedgerInvariantChecker checker : ledgerInvariantCheckers) {
            LedgerCheckResult result = checker.check(targetDate);
            checkedCount += result.checkedCount();
            mismatches.addAll(result.mismatches());
        }
        return new LedgerCheckResult(checkedCount, mismatches);
    }

    // 대사 종료. 발견한 불일치를 저장하고 실행을 MATCHED / MISMATCHED 로 닫는다
    public void finish(Long runId, LedgerCheckResult result) {
        reconciliationRunRecorder.finish(runId, result.checkedCount(), result.mismatches());
    }

    // 대사를 끝내지 못함. 실행을 FAILED 로 닫는다
    public void fail(Long runId, String reason) {
        reconciliationRunRecorder.fail(runId, reason);
    }
}
