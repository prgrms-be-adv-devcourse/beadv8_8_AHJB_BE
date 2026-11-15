package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.payment.app.dto.LedgerCheckResult;
import com.jjinmak.payment.domain.Charge;
import com.jjinmak.payment.domain.enums.MismatchTargetType;
import com.jjinmak.payment.domain.enums.WalletTransactionType;
import com.jjinmak.payment.out.repository.ChargeRepository;
import com.jjinmak.payment.out.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jjinmak.payment.domain.enums.MismatchType.CHARGE_LEDGER_MISMATCH;
import static com.jjinmak.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.CHARGE_CANCEL;

/**
 * 원장 대사 검사: 충전 건(Charge)의 상태와 원장의 충전 기록(CHARGE / CHARGE_CANCEL)이 서로 맞는가.
 *
 *   충전 건 상태          원장에 있어야 하는 기록
 *   REQUESTED · ABORTED   없음                                  (돈이 들어온 적 없음)
 *   CHARGED · USED        CHARGE +금액                          (돈이 들어와 있음)
 *   CANCELED              CHARGE +금액, CHARGE_CANCEL -금액     (들어왔다가 나감)
 *
 * 기록의 종류와 금액이 위 표와 하나라도 다르면 불일치다. 원장 기록이 가리키는 충전 건이 없어도 불일치다.
 *
 * 대상은 대상일에 지갑에 반영되거나 취소된 충전 건 + 대상일에 생긴 충전 원장 기록이 가리키는 충전 건이다.
 * 양쪽에서 모으는 이유: 한쪽만 바뀐 경우(상태만 바뀌고 원장이 없음 / 원장만 남고 상태가 그대로)를 모두 잡기 위함.
 * 판정은 그 충전 건의 원장 기록 전체로 한다 (충전은 전날, 취소는 대상일인 건도 맞게 판정된다).
 *
 *  - expected : 원장 (기록 종류, 기록 금액의 합 = 원장상 이 충전으로 들어와 있는 돈)
 *  - actual   : 충전 건 (상태, CHARGED · USED 면 충전 금액 그 외는 0 = 충전 건이 말하는 들어와 있는 돈)
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ChargeLedgerChecker implements LedgerInvariantChecker {

    static final String NO_RECORD = "NONE";
    static final String NOT_FOUND = "NOT_FOUND";

    private static final List<WalletTransactionType> CHARGE_TYPES = List.of(CHARGE, CHARGE_CANCEL);

    private final ChargeRepository chargeRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    public LedgerCheckResult check(LocalDate targetDate) {
        LocalDateTime startAt = targetDate.atStartOfDay();
        LocalDateTime endAt = targetDate.plusDays(1).atStartOfDay();

        Set<Long> chargeIds = new LinkedHashSet<>(chargeRepository.findIdsChargedOrCanceledBetween(startAt, endAt));
        chargeIds.addAll(walletTransactionRepository.findReferenceIdsCreatedBetween(PAYMENT, CHARGE_TYPES, startAt, endAt));

        List<DetectedMismatchDto> mismatches = new ArrayList<>();
        for (List<Long> chunk : LedgerRecords.chunks(chargeIds)) {
            Map<Long, Charge> charges = new HashMap<>();
            for (Charge charge : chargeRepository.findAllById(chunk)) {
                charges.put(charge.getId(), charge);
            }
            Map<Long, Map<WalletTransactionType, Long>> recordsByCharge = LedgerRecords.byReference(
                    walletTransactionRepository.findByReferenceTypeAndTypeInAndReferenceIdIn(PAYMENT, CHARGE_TYPES, chunk));

            for (Long chargeId : chunk) {
                Charge charge = charges.get(chargeId);
                Map<WalletTransactionType, Long> records = recordsByCharge.getOrDefault(chargeId, Map.of());
                if (charge == null || !records.equals(requiredRecords(charge))) {
                    mismatches.add(toMismatch(chargeId, charge, records));
                }
            }
        }
        return new LedgerCheckResult(chargeIds.size(), mismatches);
    }

    // 충전 건이 이 상태라면 원장에 있어야 하는 기록
    private Map<WalletTransactionType, Long> requiredRecords(Charge charge) {
        Map<WalletTransactionType, Long> required = new EnumMap<>(WalletTransactionType.class);
        switch (charge.getStatus()) {
            case REQUESTED, ABORTED -> { }
            case CHARGED, USED -> required.put(CHARGE, charge.getAmount());
            case CANCELED -> {
                required.put(CHARGE, charge.getAmount());
                required.put(CHARGE_CANCEL, -charge.getAmount());
            }
        }
        return required;
    }

    private DetectedMismatchDto toMismatch(Long chargeId, Charge charge, Map<WalletTransactionType, Long> records) {
        String ledgerState = records.isEmpty() ? NO_RECORD
                : new EnumMap<>(records).keySet().stream().map(Enum::name).collect(Collectors.joining("+"));
        long ledgerAmount = records.values().stream().mapToLong(Long::longValue).sum();

        if (charge == null) {
            return new DetectedMismatchDto(
                    CHARGE_LEDGER_MISMATCH, MismatchTargetType.CHARGE, String.valueOf(chargeId), null,
                    ledgerState, ledgerAmount, NOT_FOUND, null,
                    CHARGE_LEDGER_MISMATCH.getDescription() + ". 충전 건이 없음, 원장 기록: " + LedgerRecords.describe(records));
        }

        long creditedAmount = charge.isCharged() ? charge.getAmount() : 0L;
        String detail = CHARGE_LEDGER_MISMATCH.getDescription()
                + ". 충전 건 " + charge.getStatus() + " " + charge.getAmount() + "원, 원장 기록: " + LedgerRecords.describe(records);
        return new DetectedMismatchDto(
                CHARGE_LEDGER_MISMATCH, MismatchTargetType.CHARGE, String.valueOf(chargeId), null,
                ledgerState, ledgerAmount, charge.getStatus().name(), creditedAmount, detail);
    }
}
