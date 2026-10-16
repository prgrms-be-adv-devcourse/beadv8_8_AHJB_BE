package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.boundedContext.payment.app.dto.HoldDto;
import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.PaymentRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.*;

/**
 * 주문하기 → 결제 요청.
 *
 *   Payment 생성(또는 기존 것 재사용) → 잔액으로 부족분 계산
 *     부족분 0 → 지갑 차감 + 주문별 Holding 생성 → PAID
 *     부족분 > 0 → Charge(FOR_PAYMENT, REQUESTED) 생성 → NEEDS_CHARGE(pgOrderId, 부족분)
 *
 * 멱등: 같은 orderGroupId 로 다시 오면 이미 PAID 인 건 PAID 를, 아직 승인 전 충전이 있고 금액이 같으면 그 Charge 를 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class PaymentRequestUseCase {

    private final WalletSupport walletSupport;
    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;
    private final HoldingCreator holdingCreator;

    public PaymentResultDto request(PaymentRequestDto dto) {
        Wallet buyerWallet = walletSupport.getWalletByHolderId(dto.buyerId());

        Payment payment = paymentRepository.findByOrderGroupId(dto.orderGroupId())
                .orElseGet(() -> create(dto, buyerWallet));

        if (!payment.getHolderId().equals(dto.buyerId())) {
            throw new BusinessException(PAYMENT_FORBIDDEN);
        }
        if (payment.isPaid()) {
            return PaymentResultDto.paid(payment.getId(), payment.getTotalPrice());
        }

        long shortage = payment.decideCharge(buyerWallet.getBalance());

        if (shortage == 0) {
            holdingCreator.hold(toHoldDto(payment));
            payment.markPaid(LocalDateTime.now());
            return PaymentResultDto.paid(payment.getId(), payment.getTotalPrice());
        }

        Charge charge = chargeRepository.findFirstByPaymentAndStatusOrderByCreatedAtDesc(payment, REQUESTED)
                .filter(c -> c.getAmount() == shortage)
                .orElseGet(() -> chargeRepository.save(Charge.requestForPayment(buyerWallet, payment)));

        return PaymentResultDto.needsCharge(
                payment.getId(), payment.getTotalPrice(), shortage, charge.getPgOrderId(), ChargeSummaryDto.PAYMENT_ORDER_NAME);
    }

    private Payment create(PaymentRequestDto dto, Wallet buyerWallet) {
        if (dto.items() == null || dto.items().isEmpty()) {
            throw new BusinessException(PAYMENT_ITEMS_EMPTY);
        }
        Payment payment = Payment.create(dto.orderGroupId(), dto.buyerId(), buyerWallet, dto.totalPrice());
        for (PaymentRequestDto.Item item : dto.items()) {
            payment.addItem(item.orderId(), item.sellerId(), item.winningPrice(), item.deliveryFee());
        }
        payment.validateItems();
        return paymentRepository.save(payment);
    }

    /** Payment 의 아이템 스냅샷으로 홀딩 요청을 만든다. HoldDto.paymentId == Payment.id */
    static HoldDto toHoldDto(Payment payment) {
        List<HoldDto.Item> items = payment.getItems().stream()
                .map(i -> new HoldDto.Item(i.getOrderId(), i.getSellerId(), i.getWinningPrice(), i.getDeliveryFee()))
                .toList();
        return new HoldDto(payment.getId(), payment.getHolderId(), payment.getTotalPrice(), items);
    }
}
