package com.jjinmak.payment.app.support;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.payment.app.dto.HoldDto;
import com.jjinmak.payment.domain.Charge;
import com.jjinmak.payment.domain.Holding;
import com.jjinmak.payment.domain.HoldingPolicy;
import com.jjinmak.payment.domain.Wallet;
import com.jjinmak.payment.out.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.jjinmak.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.HOLD_PAY;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.HOLD_RECEIVE;
import static com.jjinmak.payment.exception.WalletErrorCode.*;

/**
 * 반드시 호출하는 유스케이스의 트랜잭션 안에서 실행된다.
 *
 * 멱등: 같은 paymentId 로 이미 홀딩이 있으면 아무 것도 하지 않는다.
 * 락 순서: 구매자 USER(낙관적) → ESCROW(비관적). 다른 지갑 로직과 같은 순서.
 * 선입선출: 구매자 지갑에서 돈을 빼기 전에 DepositConsumer 로 어떤 충전 건의 돈이 쓰이는지 기록한다.
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

    // earmarked 이 결제를 위해 방금 충전한 FOR_PAYMENT 건. 선입선출에서 가장 먼저 소비된다. 전액 예치금 결제면 null
    public void hold(HoldDto dto, Charge earmarked) {
        if (alreadyHeld(dto.paymentId())) return;
        validateTotal(dto);

        Wallet buyer = walletSupport.getWalletByHolderId(dto.holderId());
        Wallet escrow = finder.escrowForUpdate();

        if (!buyer.hasEnough(dto.totalPrice())) {
            throw new BusinessException(INSUFFICIENT_BALANCE);
        }

        depositConsumer.consume(buyer, dto.totalPrice(), earmarked);   // 지갑 차감 전에

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
