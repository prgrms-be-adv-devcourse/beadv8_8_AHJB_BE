package com.jjinmak.back.payment.app.support;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.dto.HoldDto;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.HoldingPolicy;
import com.jjinmak.back.payment.domain.PaymentShare;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.HOLD_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.HOLD_RECEIVE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.PG_HOLD_RECEIVE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_HOLD_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_HOLD_RECEIVE;
import static com.jjinmak.back.payment.exception.WalletErrorCode.*;

/**
 * 반드시 호출하는 유스케이스의 트랜잭션 안에서 실행된다.
 *
 * 결제 총액의 금액 구성(포인트 · 예치금 · 직접 결제)을 주문 금액에 비례해 주문별로 나누고, 주문마다 홀딩 1건과 원장 기록을 남긴다.
 *   포인트 몫   : 구매자 포인트 → ESCROW   (POINT_HOLD_PAY / POINT_HOLD_RECEIVE)
 *   예치금 몫   : 구매자 예치금 → ESCROW   (HOLD_PAY / HOLD_RECEIVE)
 *   직접 결제 몫 : 토스 → ESCROW           (PG_HOLD_RECEIVE 한 줄. 구매자 지갑을 거치지 않는다)
 * 어느 수단으로 냈든 ESCROW 에는 예치금으로 쌓이므로 "ESCROW 잔액 == 미정산 홀딩 합"이 그대로 성립한다.
 *
 * 멱등: 같은 paymentId 로 이미 홀딩이 있으면 아무 것도 하지 않는다.
 * 락 순서: 구매자 USER(낙관적) → ESCROW(비관적). 다른 지갑 로직과 같은 순서.
 * 선입선출: 구매자 지갑에서 예치금을 빼기 전에 DepositConsumer 로 어떤 충전 건의 돈이 쓰이는지 기록한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class HoldingCreator {

    private final WalletSupport walletSupport;
    private final SystemWalletFinder finder;
    private final HoldingPolicy holdingPolicy;
    private final LedgerRecorder ledgerRecorder;
    private final HoldingRepository holdingRepository;
    private final DepositConsumer depositConsumer;

    public void hold(HoldDto dto) {
        hold(dto, null);
    }

    // earmarked 이 결제를 위해 방금 충전한 FOR_PAYMENT 건. 선입선출에서 가장 먼저 소비된다. 충전 없이 결제하면 null
    public void hold(HoldDto dto, Charge earmarked) {
        if (alreadyHeld(dto.paymentId())) return;

        validateTotal(dto);
        PaymentShare total = dto.share();
        List<PaymentShare> shares = total.allocate(
                dto.items().stream().map(item -> item.winningPrice() + item.deliveryFee()).toList());

        Wallet buyer = walletSupport.getWalletByHolderId(dto.holderId());
        Wallet escrow = finder.escrowForUpdate();

        if (!buyer.hasEnough(total.depositAmount())) {
            throw new BusinessException(INSUFFICIENT_BALANCE);
        }
        if (!buyer.hasEnoughPoint(total.pointAmount())) {
            throw new BusinessException(INSUFFICIENT_POINT);
        }

        depositConsumer.consume(buyer, total.depositAmount(), earmarked);   // 지갑 차감 전에

        String groupId = UUID.randomUUID().toString();

        for (int i = 0; i < dto.items().size(); i++) {
            HoldDto.Item item = dto.items().get(i);
            PaymentShare share = shares.get(i);
            Holding holding = toHolding(dto.paymentId(), buyer, item, share);

            if (share.pointAmount() > 0) {
                ledgerRecorder.transferPointToDeposit(buyer, POINT_HOLD_PAY, escrow, POINT_HOLD_RECEIVE,
                        share.pointAmount(), groupId, ORDER, item.orderId());
            }
            if (share.depositAmount() > 0) {
                ledgerRecorder.transfer(buyer, HOLD_PAY, escrow, HOLD_RECEIVE,
                        share.depositAmount(), groupId, ORDER, item.orderId());
            }
            if (share.pgAmount() > 0) {
                ledgerRecorder.deposit(escrow, PG_HOLD_RECEIVE, share.pgAmount(), ORDER, item.orderId());
            }

            holdingRepository.save(holding);
        }
    }

    private Holding toHolding(Long paymentId, Wallet buyer, HoldDto.Item item, PaymentShare share) {
        Wallet sellerWallet = walletSupport.getWalletByHolderId(item.sellerId());
        long fee = holdingPolicy.feeOf(item.winningPrice());

        return Holding.hold(item.orderId(), paymentId, buyer, sellerWallet, item.winningPrice(), item.deliveryFee(), fee, share);
    }

    private boolean alreadyHeld(Long paymentId) {
        return holdingRepository.existsByPaymentId(paymentId);
    }

    private void validateTotal(HoldDto dto) {
        if (dto.items() == null || dto.items().isEmpty()) {
            throw new BusinessException(HOLD_ITEMS_EMPTY);
        }
        long sum = dto.items().stream()
                .mapToLong(item -> item.winningPrice() + item.deliveryFee())
                .sum();
        if (sum != dto.totalPrice() || dto.share() == null || dto.share().total() != dto.totalPrice()) {
            throw new BusinessException(HOLD_AMOUNT_MISMATCH);
        }
    }
}
