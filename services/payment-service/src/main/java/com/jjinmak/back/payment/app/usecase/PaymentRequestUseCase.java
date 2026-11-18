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
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentPlan;
import com.jjinmak.back.payment.domain.PaymentPlanner;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.jjinmak.back.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_FORBIDDEN;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_ITEMS_EMPTY;

/**
 * 주문하기 → 결제 요청.
 *
 *   Payment 생성(또는 기존 것 재사용) → 결제수단 · 포인트 사용액 · 지금 잔액으로 결제 금액 구성 결정 (PaymentPlanner)
 *     결제창이 필요 없음 (포인트 + 예치금으로 전액 충당)
 *         → 포인트 · 예치금 차감 + 주문별 Holding 생성 → PAID
 *     예치금 결제인데 예치금이 모자람
 *         → Charge(FOR_PAYMENT, REQUESTED) 생성 → NEEDS_CHARGE(pgOrderId, 충전 금액, 부족분, 계좌이체)
 *     직접 결제 (계좌이체 · 카드)
 *         → Charge(DIRECT_PAYMENT, REQUESTED) 생성 → NEEDS_PAYMENT(pgOrderId, 결제 금액, 고른 수단)
 *
 * 결제수단을 보내지 않으면 예치금 결제, 포인트 사용액을 보내지 않으면 0 이다 (결제수단 선택 전의 요청과 똑같이 동작한다).
 *
 * 충전 금액: 부족분이 최소 충전 금액보다 작으면 최소 충전 금액으로 올린다. 차액은 결제 후 예치금에 남는다.
 * 한도: 부족분 충전과 계좌이체 직접 결제는 1회 한도를 넘으면 결제창을 띄우지 않고 거절한다.
 *
 * 멱등: 같은 orderGroupId 로 다시 오면 이미 PAID 인 건 PAID 를 돌려준다.
 * 다시 주문하기: 아직 결제 전이면 금액 구성을 다시 정한다 (잔액이나 고른 수단이 바뀌었을 수 있다).
 *   지금 계획과 같은 승인 전 결제 건(Charge)이 있으면 그 건을 돌려주고, 없으면 새로 만든다.
 *   예전 계획으로 만든 건은 승인 단계에서 거절된다 (PaymentConfirmUseCase, CHARGE_SUPERSEDED).
 */
@Service
@RequiredArgsConstructor
public class PaymentRequestUseCase {

    private final WalletSupport walletSupport;
    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;
    private final HoldingCreator holdingCreator;
    private final PaymentPlanner paymentPlanner;
    private final EventPublisher eventPublisher;

    public PaymentResultDto request(PaymentRequestDto dto) {
        Wallet buyerWallet = walletSupport.getWalletByHolderId(dto.buyerId());
        PayMethod payMethod = PayMethod.from(dto.payMethod());

        Payment payment = paymentRepository.findByOrderGroupId(dto.orderGroupId())
                .orElseGet(() -> create(dto, buyerWallet));

        if (!payment.getHolderId().equals(dto.buyerId())) {
            throw new BusinessException(PAYMENT_FORBIDDEN);
        }
        if (payment.isPaid()) {
            return PaymentResultDto.paid(payment.getId(), payment.getTotalPrice(), breakdownOf(payment));
        }

        PaymentPlan plan = paymentPlanner.plan(payment.getTotalPrice(), payMethod, dto.pointUseAmount(),
                buyerWallet.getBalance(), buyerWallet.getPointBalance());
        paymentPlanner.validateLimit(plan);
        payment.decide(plan);

        if (!plan.needsPgWindow()) {
            holdingCreator.hold(toHoldDto(payment));
            payment.markPaid(LocalDateTime.now());
            // 결제창 경로(confirm)와 같은 이벤트. 주문은 이 이벤트 하나로 결제 완료를 안다
            eventPublisher.publish(PaymentPaidEvent.of(
                    payment.getOrderGroupId(), payment.getId(), payment.getHolderId(), payment.getTotalPrice(), payment.getPaidAt()));
            return PaymentResultDto.paid(payment.getId(), payment.getTotalPrice(), breakdownOf(payment));
        }

        Charge charge = findReusableCharge(payment)
                .orElseGet(() -> chargeRepository.save(newCharge(buyerWallet, payment)));

        if (payment.isDirectPayment()) {
            return PaymentResultDto.needsPayment(
                    payment.getId(), payment.getTotalPrice(),
                    charge.getPgOrderId(), ChargeSummaryDto.DIRECT_ORDER_NAME, charge.getRequiredMethod().name(),
                    breakdownOf(payment));
        }
        return PaymentResultDto.needsCharge(
                payment.getId(), payment.getTotalPrice(), plan.chargeAmount(), plan.shortage(),
                charge.getPgOrderId(), ChargeSummaryDto.PAYMENT_ORDER_NAME, charge.getRequiredMethod().name(),
                breakdownOf(payment));
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

    // 결제창을 닫고 다시 주문하기를 누른 경우: 지금 계획과 같은 승인 전 건이 있으면 그 건으로 결제창을 다시 띄운다 (가장 최근 건)
    private Optional<Charge> findReusableCharge(Payment payment) {
        return chargeRepository.findByPaymentOrderByIdDesc(payment).stream()
                .filter(charge -> charge.getStatus() == REQUESTED)
                .filter(Charge::matchesPaymentPlan)
                .findFirst();
    }

    private Charge newCharge(Wallet buyerWallet, Payment payment) {
        return payment.isDirectPayment()
                ? Charge.requestDirectPayment(buyerWallet, payment, payment.getPayMethod().pgMethod())
                : Charge.requestForPayment(buyerWallet, payment);
    }

    static PaymentResultDto.Breakdown breakdownOf(Payment payment) {
        return new PaymentResultDto.Breakdown(payment.getPayMethod().name(),
                payment.getPointAmount(), payment.getDepositAmount(), payment.getPgAmount());
    }

    static HoldDto toHoldDto(Payment payment) {
        List<HoldDto.Item> items = payment.getItems().stream()
                .map(i -> new HoldDto.Item(i.getOrderId(), i.getSellerId(), i.getWinningPrice(), i.getDeliveryFee()))
                .toList();
        return new HoldDto(payment.getId(), payment.getHolderId(), payment.getTotalPrice(), items, payment.getShare());
    }
}
