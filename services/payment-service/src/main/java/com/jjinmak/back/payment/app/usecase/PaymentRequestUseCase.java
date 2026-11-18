package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.payment.app.dto.HoldDto;
import com.jjinmak.back.payment.app.support.HoldingCreator;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.ChargePolicy;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.back.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_FORBIDDEN;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_ITEMS_EMPTY;

/**
 * 주문하기 → 결제 요청.
 *
 *   Payment 생성(또는 기존 것 재사용) → 잔액으로 부족분 계산 → 충전 정책으로 충전 금액 결정
 *     부족분 0 → 지갑 차감 + 주문별 Holding 생성 → PAID
 *     부족분 > 0 → Charge(FOR_PAYMENT, REQUESTED) 생성 → NEEDS_CHARGE(pgOrderId, 충전 금액, 부족분, 결제수단)
 *
 * 충전 금액: 부족분이 최소 충전 금액보다 작으면 최소 충전 금액으로 올린다. 차액은 결제 후 예치금에 남는다.
 *           1회 충전 한도를 넘으면 결제창을 띄우지 않고 CHARGE_LIMIT_EXCEEDED.
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
    private final ChargePolicy chargePolicy;
    private final EventPublisher eventPublisher;

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

        long shortage = payment.shortageOf(buyerWallet.getBalance());
        long chargeAmount = chargePolicy.chargeAmountFor(shortage);
        chargePolicy.validateChargeable(chargeAmount);
        payment.decideCharge(chargeAmount);

        if (chargeAmount == 0) {
            holdingCreator.hold(toHoldDto(payment));
            payment.markPaid(LocalDateTime.now());
            // 결제창 경로(confirm)와 같은 이벤트. 주문은 이 이벤트 하나로 결제 완료를 안다
            eventPublisher.publish(PaymentPaidEvent.of(
                    payment.getOrderGroupId(), payment.getId(), payment.getHolderId(), payment.getTotalPrice(), payment.getPaidAt()));
            return PaymentResultDto.paid(payment.getId(), payment.getTotalPrice());
        }

        Charge charge = chargeRepository.findFirstByPaymentAndStatusOrderByCreatedAtDesc(payment, REQUESTED)
                .filter(c -> c.getAmount() == chargeAmount)
                .orElseGet(() -> chargeRepository.save(Charge.requestForPayment(buyerWallet, payment)));

        return PaymentResultDto.needsCharge(
                payment.getId(), payment.getTotalPrice(), chargeAmount, shortage,
                charge.getPgOrderId(), ChargeSummaryDto.PAYMENT_ORDER_NAME, charge.getRequiredMethod().name());
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

    static HoldDto toHoldDto(Payment payment) {
        List<HoldDto.Item> items = payment.getItems().stream()
                .map(i -> new HoldDto.Item(i.getOrderId(), i.getSellerId(), i.getWinningPrice(), i.getDeliveryFee()))
                .toList();
        return new HoldDto(payment.getId(), payment.getHolderId(), payment.getTotalPrice(), items);
    }
}
