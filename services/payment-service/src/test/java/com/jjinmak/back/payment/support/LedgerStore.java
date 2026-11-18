package com.jjinmak.back.payment.support;

import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.enums.ReferenceType;
import com.jjinmak.back.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 테스트용 원장. DB 없이, 넣어 둔 원장 기록을 충전 · 홀딩 대사 검사가 쓰는 조회 2개로 찾아 준다.
 * repository 를 WalletTransactionRepository 자리에 넣어 쓴다.
 */
public class LedgerStore {

    public final WalletTransactionRepository repository = mock(WalletTransactionRepository.class);
    public final List<WalletTransaction> rows = new ArrayList<>();
    // findByReferenceTypeAndTypeInAndReferenceIdIn 이 불릴 때마다 넘어온 대상 id 개수
    public final List<Integer> inQuerySizes = new ArrayList<>();

    // 검사는 기록이 어느 지갑 것인지 보지 않으므로 지갑 하나를 함께 쓴다
    private final Wallet wallet = new Wallet(null, WalletType.USER);

    public LedgerStore() {
        when(repository.findReferenceIdsCreatedBetween(any(), any(), any(), any())).thenAnswer(invocation -> {
            ReferenceType referenceType = invocation.getArgument(0);
            Collection<WalletTransactionType> types = invocation.getArgument(1);
            LocalDateTime startAt = invocation.getArgument(2);
            LocalDateTime endAt = invocation.getArgument(3);
            return rows.stream()
                    .filter(row -> row.getReferenceType() == referenceType && types.contains(row.getType()))
                    .filter(row -> !row.getCreatedAt().isBefore(startAt) && row.getCreatedAt().isBefore(endAt))
                    .map(WalletTransaction::getReferenceId)
                    .distinct()
                    .toList();
        });
        when(repository.findByReferenceTypeAndTypeInAndReferenceIdIn(any(), any(), any())).thenAnswer(invocation -> {
            ReferenceType referenceType = invocation.getArgument(0);
            Collection<WalletTransactionType> types = invocation.getArgument(1);
            Collection<Long> referenceIds = invocation.getArgument(2);
            inQuerySizes.add(referenceIds.size());
            return rows.stream()
                    .filter(row -> row.getReferenceType() == referenceType && types.contains(row.getType()))
                    .filter(row -> referenceIds.contains(row.getReferenceId()))
                    .toList();
        });
    }

    // 원장 기록 1줄. signedAmount 는 부호 있는 금액(입금 +, 출금 -), createdAt 은 기록된 시각
    public LedgerStore add(ReferenceType referenceType, long referenceId, WalletTransactionType type,
                           long signedAmount, LocalDateTime createdAt) {
        WalletTransaction row = WalletTransaction.of(wallet, type, signedAmount, "group", referenceType, referenceId);
        ReflectionTestUtils.setField(row, "createdAt", createdAt);
        rows.add(row);
        return this;
    }
}
