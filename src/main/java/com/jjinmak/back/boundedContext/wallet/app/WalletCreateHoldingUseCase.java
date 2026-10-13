package com.jjinmak.back.boundedContext.wallet.app;

import com.jjinmak.back.boundedContext.wallet.domain.Holding;
import com.jjinmak.back.boundedContext.wallet.domain.HoldingPolicy;
import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.wallet.out.HoldingRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletTransactionRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.wallet.dto.HoldDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.wallet.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.boundedContext.wallet.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType.HOLD_PAY;
import static com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType.HOLD_RECEIVE;
import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WalletCreateHoldingUseCase {

    public final WalletSupport walletSupport;
    public final SystemWalletFinder finder;
    public final HoldingPolicy holdingPolicy;
    public final WalletTransactionRepository walletTransactionRepository;
    public final HoldingRepository holdingRepository;

    public void hold(HoldDto dto) {
        if (alreadyHeld(dto.paymentId())) return;
        validateTotal(dto);

        Wallet buyer = walletSupport.findWalletByHolderId(dto.holderId())
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND));
        Wallet escrow = finder.escrowForUpdate();

        if (!buyer.hasEnough(dto.totalPrice())) {
            throw new BusinessException(INSUFFICIENT_BALANCE);
        }

        String groupId = UUID.randomUUID().toString();

        for (HoldDto.Item item : dto.items()) {
            Holding holding = toHolding(dto.paymentId(), buyer, item);
            long amount = holding.getAmount();

            // 상품별 돈 이동
            buyer.withdrawal(amount);
            escrow.deposit(amount);

            // 상품별 원장 기록
            walletTransactionRepository.save(
                    WalletTransaction.of(buyer,  HOLD_PAY,     -amount, groupId, ORDER, item.orderId()));
            walletTransactionRepository.save(
                    WalletTransaction.of(escrow, HOLD_RECEIVE,  amount, groupId, ORDER, item.orderId()));

            holdingRepository.save(holding);
        }

    }

    private Holding toHolding(Long paymentId, Wallet buyer, HoldDto.Item item) {
        Wallet sellerWallet = walletSupport.findWalletByHolderId(item.sellerId())
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND));

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

