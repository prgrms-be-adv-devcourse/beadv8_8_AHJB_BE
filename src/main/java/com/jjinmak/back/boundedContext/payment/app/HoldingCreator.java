package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.HoldDto;
import com.jjinmak.back.boundedContext.payment.domain.Holding;
import com.jjinmak.back.boundedContext.payment.domain.HoldingPolicy;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.out.HoldingRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.HOLD_PAY;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.HOLD_RECEIVE;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.*;

/**
 * 주문 결제의 홀딩 생성: 구매자 지갑 → ESCROW 로 총액을 옮기고 주문당 Holding 을 만든다.
 *
 * 유스케이스가 아니라 LedgerRecorder 처럼 유스케이스가 쓰는 지원 컴포넌트다.
 * (전액 예치금 결제와 부족분 충전 후 결제, 두 유스케이스가 같이 쓴다)
 * 반드시 호출하는 유스케이스의 트랜잭션 안에서 실행된다 (MANDATORY).
 *
 * 멱등: 같은 paymentId 로 이미 홀딩이 있으면 아무 것도 하지 않는다.
 * 락 순서: 구매자 USER(낙관적) → ESCROW(비관적). 다른 지갑 로직과 같은 순서.
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

    public void hold(HoldDto dto) {
        if (alreadyHeld(dto.paymentId())) return;
        validateTotal(dto);

        Wallet buyer = walletSupport.getWalletByHolderId(dto.holderId());
        Wallet escrow = finder.escrowForUpdate();

        if (!buyer.hasEnough(dto.totalPrice())) {
            throw new BusinessException(INSUFFICIENT_BALANCE);
        }

        String groupId = UUID.randomUUID().toString();

        for (HoldDto.Item item : dto.items()) {
            Holding holding = toHolding(dto.paymentId(), buyer, item);

            ledgerRecorder.transfer(buyer, HOLD_PAY, escrow, HOLD_RECEIVE,
                    holding.getAmount(), groupId, ORDER, item.orderId());

            holdingRepository.save(holding);
        }
    }

    private Holding toHolding(Long paymentId, Wallet buyer, HoldDto.Item item) {
        Wallet sellerWallet = walletSupport.getWalletByHolderId(item.sellerId());
        long fee = holdingPolicy.feeOf(item.winningPrice());
        return Holding.hold(item.orderId(), paymentId, buyer, sellerWallet, item.winningPrice(), item.deliveryFee(), fee);
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
        if (sum != dto.totalPrice()) {
            throw new BusinessException(HOLD_AMOUNT_MISMATCH);
        }
    }
}
