package com.jjinmak.back.payment.support;

import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.out.repository.ChargeRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 테스트용 충전 건 저장소. DB 없이, 넣어 둔 충전 건을 대사가 쓰는 조회 3개로 찾아 준다.
 * repository 를 ChargeRepository 자리에 넣어 쓴다.
 */
public class ChargeStore {

    public final ChargeRepository repository = mock(ChargeRepository.class);
    public final List<Charge> charges = new ArrayList<>();
    // findByPgOrderIdIn 이 불릴 때마다 넘어온 주문번호 개수
    public final List<Integer> inQuerySizes = new ArrayList<>();

    private long sequence = 0;

    public ChargeStore() {
        when(repository.findByPgOrderId(any())).thenAnswer(invocation -> {
            String pgOrderId = invocation.getArgument(0);
            return charges.stream().filter(charge -> charge.getPgOrderId().equals(pgOrderId)).findFirst();
        });
        when(repository.findByPgOrderIdIn(any())).thenAnswer(invocation -> {
            Collection<String> pgOrderIds = invocation.getArgument(0);
            inQuerySizes.add(pgOrderIds.size());
            return charges.stream().filter(charge -> pgOrderIds.contains(charge.getPgOrderId())).toList();
        });
        when(repository.findApprovedOrCanceledBetween(any(), any())).thenAnswer(invocation -> {
            LocalDateTime startAt = invocation.getArgument(0);
            LocalDateTime endAt = invocation.getArgument(1);
            return charges.stream()
                    .filter(charge -> within(charge.getApprovedAt(), startAt, endAt)
                            || within(charge.getCanceledAt(), startAt, endAt))
                    .toList();
        });
    }

    public Charge add(String pgOrderId, ChargeStatus status, long amount) {
        return add(pgOrderId, status, amount, null, null);
    }

    public Charge add(String pgOrderId, ChargeStatus status, long amount,
                      LocalDateTime approvedAt, LocalDateTime canceledAt) {
        Charge charge = ChargeFixture.charge(++sequence, pgOrderId, status, amount, approvedAt, canceledAt);
        charges.add(charge);
        return charge;
    }

    // startAt 이상, endAt 미만
    private static boolean within(LocalDateTime time, LocalDateTime startAt, LocalDateTime endAt) {
        return time != null && !time.isBefore(startAt) && time.isBefore(endAt);
    }
}
