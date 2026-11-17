package com.jjinmak.back.payment.support;

import com.jjinmak.back.payment.domain.ReconciliationMismatch;
import com.jjinmak.back.payment.domain.ReconciliationRun;
import com.jjinmak.back.payment.domain.enums.ReconciliationType;
import com.jjinmak.back.payment.out.repository.ReconciliationMismatchRepository;
import com.jjinmak.back.payment.out.repository.ReconciliationRunRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 테스트용 대사 기록 저장소. DB 없이, 저장한 실행(Run)과 불일치를 메모리에 두고 대사가 쓰는 조회로 찾아 준다.
 * runRepository / mismatchRepository 를 리포지토리 자리에 넣어 쓴다.
 */
public class ReconciliationStore {

    public final ReconciliationRunRepository runRepository = mock(ReconciliationRunRepository.class);
    public final ReconciliationMismatchRepository mismatchRepository = mock(ReconciliationMismatchRepository.class);
    public final List<ReconciliationRun> runs = new ArrayList<>();
    public final List<ReconciliationMismatch> mismatches = new ArrayList<>();

    private long sequence = 0;
    private boolean conflictOnSave = false;

    public ReconciliationStore() {
        when(runRepository.findByTypeAndTargetDate(any(), any())).thenAnswer(invocation -> {
            ReconciliationType type = invocation.getArgument(0);
            LocalDate targetDate = invocation.getArgument(1);
            return runs.stream()
                    .filter(run -> run.getType() == type && run.getTargetDate().equals(targetDate))
                    .findFirst();
        });
        when(runRepository.saveAndFlush(any(ReconciliationRun.class))).thenAnswer(invocation -> {
            if (conflictOnSave) {
                throw new DataIntegrityViolationException("uk_reconciliation_run_type_date");
            }
            return save(invocation.getArgument(0));
        });
        when(runRepository.findById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return runs.stream().filter(run -> run.getId().equals(id)).findFirst();
        });
        when(mismatchRepository.findByRun(any())).thenAnswer(invocation -> {
            ReconciliationRun run = invocation.getArgument(0);
            return mismatches.stream().filter(mismatch -> mismatch.getRun() == run).toList();
        });
        when(mismatchRepository.save(any(ReconciliationMismatch.class))).thenAnswer(invocation -> {
            ReconciliationMismatch mismatch = invocation.getArgument(0);
            mismatches.add(mismatch);
            return mismatch;
        });
    }

    // 이미 있는 실행을 넣어 둔다. DB 가 하듯 id 를 매긴다
    public ReconciliationRun save(ReconciliationRun run) {
        ReflectionTestUtils.setField(run, "id", ++sequence);
        runs.add(run);
        return run;
    }

    // 다른 서버가 같은 실행을 먼저 저장한 상황: 새 실행을 저장하면 유니크 제약 위반이 난다
    public void conflictOnSave() {
        this.conflictOnSave = true;
    }

    // 주문번호로 불일치 찾기. 없으면 null
    public ReconciliationMismatch mismatchOf(String pgOrderId) {
        return mismatches.stream()
                .filter(mismatch -> pgOrderId.equals(mismatch.getPgOrderId()))
                .findFirst()
                .orElse(null);
    }
}
