package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.back.payment.app.dto.LedgerCheckResult;
import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.enums.MismatchTargetType;
import com.jjinmak.back.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
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

import static com.jjinmak.back.payment.domain.enums.HoldingStatus.CONFIRMED;
import static com.jjinmak.back.payment.domain.enums.HoldingStatus.HELD;
import static com.jjinmak.back.payment.domain.enums.HoldingStatus.REFUNDED;
import static com.jjinmak.back.payment.domain.enums.HoldingStatus.RELEASED;
import static com.jjinmak.back.payment.domain.enums.MismatchType.HOLDING_LEDGER_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.FEE_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.FEE_RECEIVE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.HOLD_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.HOLD_RECEIVE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.REFUND_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.REFUND_RECEIVE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.SETTLEMENT_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.SETTLEMENT_RECEIVE;

/**
 * 원장 대사 검사: 홀딩(Holding)의 상태와 그 주문의 원장 기록이 서로 맞는가.
 *
 *   홀딩 상태          원장에 있어야 하는 기록 (금액 = 홀딩 금액, 정산액 = 금액 - 수수료)
 *   HELD · CONFIRMED   HOLD_PAY -금액, HOLD_RECEIVE +금액
 *   REFUNDED           위 + REFUND_PAY -금액, REFUND_RECEIVE +금액
 *   RELEASED           위 + SETTLEMENT_PAY -정산액, SETTLEMENT_RECEIVE +정산액
 *                         + 수수료가 있으면 FEE_PAY -수수료, FEE_RECEIVE +수수료
 *
 * 기록의 종류와 금액이 위 표와 하나라도 다르면 불일치다. 원장 기록이 가리키는 주문의 홀딩이 없어도 불일치다.
 * 구매 확정(CONFIRMED)은 돈이 움직이지 않아 원장 기록이 없으므로 HELD 와 같은 기록을 갖는다.
 *
 * 대상은 대상일에 만들어지거나 환불 · 정산된 홀딩 + 대상일에 생긴 원장 기록이 가리키는 주문이다.
 * 판정은 그 주문의 원장 기록 전체로 한다 (결제는 전날, 정산은 대상일인 건도 맞게 판정된다).
 *
 *  - 대상 식별자 : 주문 id (원장이 주문 id 로 기록되고 홀딩은 주문마다 1개다)
 *  - expected   : 원장 (기록으로 본 단계, ESCROW 쪽 기록의 합 = 원장상 이 주문 몫으로 ESCROW 에 남은 돈)
 *  - actual     : 홀딩 (상태, HELD · CONFIRMED 면 홀딩 금액 그 외는 0 = 홀딩이 말하는 ESCROW 에 남은 돈)
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class HoldingLedgerChecker implements LedgerInvariantChecker {

    static final String NO_RECORD = "NONE";
    static final String NOT_FOUND = "NOT_FOUND";

    private static final List<WalletTransactionType> HOLDING_TYPES = List.of(
            HOLD_PAY, HOLD_RECEIVE, REFUND_PAY, REFUND_RECEIVE, SETTLEMENT_PAY, SETTLEMENT_RECEIVE, FEE_PAY, FEE_RECEIVE);

    // ESCROW 지갑 쪽에 남는 기록. 이 금액들의 합이 원장상 이 주문 몫으로 ESCROW 에 남아 있는 돈이다
    private static final List<WalletTransactionType> ESCROW_SIDE_TYPES = List.of(HOLD_RECEIVE, REFUND_PAY, SETTLEMENT_PAY, FEE_PAY);

    private final HoldingRepository holdingRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    public LedgerCheckResult check(LocalDate targetDate) {
        LocalDateTime startAt = targetDate.atStartOfDay();
        LocalDateTime endAt = targetDate.plusDays(1).atStartOfDay();

        Set<Long> orderIds = new LinkedHashSet<>(holdingRepository.findOrderIdsChangedBetween(startAt, endAt));
        orderIds.addAll(walletTransactionRepository.findReferenceIdsCreatedBetween(ORDER, HOLDING_TYPES, startAt, endAt));

        List<DetectedMismatchDto> mismatches = new ArrayList<>();
        for (List<Long> chunk : LedgerRecords.chunks(orderIds)) {
            Map<Long, Holding> holdings = new HashMap<>();
            for (Holding holding : holdingRepository.findByOrderIdIn(chunk)) {
                holdings.put(holding.getOrderId(), holding);
            }
            Map<Long, Map<WalletTransactionType, Long>> recordsByOrder = LedgerRecords.byReference(
                    walletTransactionRepository.findByReferenceTypeAndTypeInAndReferenceIdIn(ORDER, HOLDING_TYPES, chunk));

            for (Long orderId : chunk) {
                Holding holding = holdings.get(orderId);
                Map<WalletTransactionType, Long> records = recordsByOrder.getOrDefault(orderId, Map.of());
                if (holding == null || !records.equals(requiredRecords(holding))) {
                    mismatches.add(toMismatch(orderId, holding, records));
                }
            }
        }
        return new LedgerCheckResult(orderIds.size(), mismatches);
    }

    // 홀딩이 이 상태라면 원장에 있어야 하는 기록
    private Map<WalletTransactionType, Long> requiredRecords(Holding holding) {
        long amount = holding.getAmount();
        Map<WalletTransactionType, Long> required = new EnumMap<>(WalletTransactionType.class);
        required.put(HOLD_PAY, -amount);
        required.put(HOLD_RECEIVE, amount);

        switch (holding.getStatus()) {
            case HELD, CONFIRMED -> { }
            case REFUNDED -> {
                required.put(REFUND_PAY, -amount);
                required.put(REFUND_RECEIVE, amount);
            }
            case RELEASED -> {
                required.put(SETTLEMENT_PAY, -holding.getSettlementAmount());
                required.put(SETTLEMENT_RECEIVE, holding.getSettlementAmount());
                if (holding.hasFee()) {
                    required.put(FEE_PAY, -holding.getFeeAmount());
                    required.put(FEE_RECEIVE, holding.getFeeAmount());
                }
            }
        }
        return required;
    }

    private DetectedMismatchDto toMismatch(Long orderId, Holding holding, Map<WalletTransactionType, Long> records) {
        String ledgerState = ledgerStateOf(records);
        long ledgerEscrowAmount = ESCROW_SIDE_TYPES.stream().mapToLong(type -> records.getOrDefault(type, 0L)).sum();

        if (holding == null) {
            return new DetectedMismatchDto(
                    HOLDING_LEDGER_MISMATCH, MismatchTargetType.HOLDING, String.valueOf(orderId), null,
                    ledgerState, ledgerEscrowAmount, NOT_FOUND, null,
                    HOLDING_LEDGER_MISMATCH.getDescription() + ". 홀딩이 없음, 원장 기록: " + LedgerRecords.describe(records));
        }

        boolean inEscrow = holding.getStatus() == HELD || holding.getStatus() == CONFIRMED;
        long heldAmount = inEscrow ? holding.getAmount() : 0L;
        String detail = HOLDING_LEDGER_MISMATCH.getDescription()
                + ". 홀딩 " + holding.getStatus() + " " + holding.getAmount() + "원(수수료 " + holding.getFeeAmount() + "원)"
                + ", 원장 기록: " + LedgerRecords.describe(records);
        return new DetectedMismatchDto(
                HOLDING_LEDGER_MISMATCH, MismatchTargetType.HOLDING, String.valueOf(orderId), null,
                ledgerState, ledgerEscrowAmount, holding.getStatus().name(), heldAmount, detail);
    }

    // 원장 기록만 보고 판단한 단계. 구매 확정은 원장에 남지 않아 HELD 로 보인다
    private static String ledgerStateOf(Map<WalletTransactionType, Long> records) {
        if (records.isEmpty()) {
            return NO_RECORD;
        }
        boolean refunded = records.containsKey(REFUND_PAY) || records.containsKey(REFUND_RECEIVE);
        boolean released = records.containsKey(SETTLEMENT_PAY) || records.containsKey(SETTLEMENT_RECEIVE)
                || records.containsKey(FEE_PAY) || records.containsKey(FEE_RECEIVE);
        if (refunded && released) {
            return REFUNDED + "+" + RELEASED;
        }
        if (refunded) {
            return REFUNDED.name();
        }
        if (released) {
            return RELEASED.name();
        }
        return HELD.name();
    }
}
