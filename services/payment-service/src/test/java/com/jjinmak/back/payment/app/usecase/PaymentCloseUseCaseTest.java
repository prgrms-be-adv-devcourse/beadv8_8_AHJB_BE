package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentPlan;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.PaymentCloseReason;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentCloseResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 주문이 버린 주문 그룹의 결제 닫기: 결제 상태별 결과, 승인 전 결제창 무효화, 멱등, 소유자 확인.
 */
class PaymentCloseUseCaseTest {

    private static final long BUYER_ID = 1L;
    private static final long OTHER_ID = 9L;
    private static final long ORDER_GROUP_ID = 100L;
    private static final long PAYMENT_ID = 7L;
    private static final long TOTAL = 30_000L;

    private PaymentCloseUseCase useCase;
    private PaymentRepository paymentRepository;

    private Wallet wallet;
    private Payment payment;
    private final List<Charge> charges = new ArrayList<>();

    @BeforeEach
    void setUp() {
        wallet = new Wallet(null, WalletType.USER);
        payment = Payment.create(ORDER_GROUP_ID, BUYER_ID, wallet, TOTAL);
        payment.addItem(1_001L, 2L, TOTAL, 0);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);

        paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findByOrderGroupIdForUpdate(ORDER_GROUP_ID)).thenReturn(Optional.of(payment));

        ChargeRepository chargeRepository = mock(ChargeRepository.class);
        when(chargeRepository.findByPaymentOrderByIdDesc(payment)).thenAnswer(invocation -> new ArrayList<>(charges));

        useCase = new PaymentCloseUseCase(paymentRepository, chargeRepository);
    }

    // 카드 직접 결제로 결제창을 띄운 상태 (Payment CHARGING + Charge REQUESTED)
    private Charge openCardWindow() {
        payment.decide(new PaymentPlan(PayMethod.CARD, 0L, 0L, TOTAL, TOTAL, 0L, false));
        Charge charge = Charge.requestDirectPayment(wallet, payment, PayMethod.CARD.pgMethod());
        charges.add(charge);
        return charge;
    }

    // 예치금 결제의 부족분 충전으로 결제창을 띄운 상태
    private Charge openTopUpWindow(long chargeAmount) {
        payment.decideCharge(chargeAmount);
        Charge charge = Charge.requestForPayment(wallet, payment);
        charges.add(charge);
        return charge;
    }

    @Test
    @DisplayName("결제창이 떠 있는 결제를 닫으면 결제는 FAILED, 승인 전 결제창은 ABORTED 가 되고 CLOSED 를 돌려준다")
    void close_chargingPayment() {
        Charge charge = openCardWindow();

        PaymentCloseResultDto result = useCase.closeByOrder(ORDER_GROUP_ID, BUYER_ID);

        assertThat(result.status()).isEqualTo(PaymentCloseResultDto.Status.CLOSED);
        assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.canDiscardOrderGroup()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isNotNull();
        assertThat(payment.getFailureReason()).isEqualTo(PaymentCloseReason.CLOSED_BY_ORDER.message());
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.ABORTED);
        assertThat(charge.getFailureCode()).isEqualTo(PaymentCloseReason.CLOSED_BY_ORDER.code());
    }

    @Test
    @DisplayName("부족분 충전 결제창도 같은 방식으로 닫힌다")
    void close_topUpWindow() {
        Charge charge = openTopUpWindow(10_000L);

        PaymentCloseResultDto result = useCase.closeByOrder(ORDER_GROUP_ID, BUYER_ID);

        assertThat(result.status()).isEqualTo(PaymentCloseResultDto.Status.CLOSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.ABORTED);
    }

    @Test
    @DisplayName("이미 끝난 결제창(ABORTED)은 건드리지 않고, 승인 전 결제창만 닫는다")
    void close_onlyRequestedCharges() {
        Charge expired = openCardWindow();
        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(30);
        expired.markAborted(expiredAt, "EXPIRED", "결제창 유효 시간 만료");
        Charge reopened = openCardWindow();

        useCase.closeByOrder(ORDER_GROUP_ID, BUYER_ID);

        assertThat(expired.getFailureCode()).isEqualTo("EXPIRED");
        assertThat(expired.getAbortedAt()).isEqualTo(expiredAt);
        assertThat(reopened.getStatus()).isEqualTo(ChargeStatus.ABORTED);
        assertThat(reopened.getFailureCode()).isEqualTo(PaymentCloseReason.CLOSED_BY_ORDER.code());
    }

    @Test
    @DisplayName("같은 닫기 요청이 다시 와도 결과가 같고, 처음 닫힌 시각과 사유가 바뀌지 않는다")
    void close_isIdempotent() {
        openCardWindow();
        PaymentCloseResultDto first = useCase.closeByOrder(ORDER_GROUP_ID, BUYER_ID);
        LocalDateTime failedAt = payment.getFailedAt();

        PaymentCloseResultDto second = useCase.closeByOrder(ORDER_GROUP_ID, BUYER_ID);
        PaymentCloseResultDto byEvent = useCase.closeOnOrderDeleted(ORDER_GROUP_ID);

        assertThat(second).isEqualTo(first);
        assertThat(byEvent).isEqualTo(first);
        assertThat(payment.getFailedAt()).isEqualTo(failedAt);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentCloseReason.CLOSED_BY_ORDER.message());
    }

    @Test
    @DisplayName("이미 결제가 끝났으면 닫지 않고 ALREADY_PAID 를 돌려준다")
    void close_paidPayment() {
        Charge charge = openCardWindow();
        LocalDateTime now = LocalDateTime.now();
        charge.markPaidDirectly("pk-1", now, now, "카드", null);
        payment.markPaid(now, "카드");

        PaymentCloseResultDto result = useCase.closeByOrder(ORDER_GROUP_ID, BUYER_ID);

        assertThat(result.status()).isEqualTo(PaymentCloseResultDto.Status.ALREADY_PAID);
        assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.canDiscardOrderGroup()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.USED);
    }

    @Test
    @DisplayName("결제가 만들어지기 전의 주문 그룹이면 NOT_FOUND 를 돌려준다")
    void close_noPayment() {
        when(paymentRepository.findByOrderGroupIdForUpdate(555L)).thenReturn(Optional.empty());

        PaymentCloseResultDto result = useCase.closeByOrder(555L, BUYER_ID);

        assertThat(result.status()).isEqualTo(PaymentCloseResultDto.Status.NOT_FOUND);
        assertThat(result.paymentId()).isNull();
        assertThat(result.canDiscardOrderGroup()).isTrue();
    }

    @Test
    @DisplayName("결제의 구매자가 아니면 닫을 수 없고 결제는 그대로다")
    void close_byOther_forbidden() {
        Charge charge = openCardWindow();

        assertThatThrownBy(() -> useCase.closeByOrder(ORDER_GROUP_ID, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PAYMENT_FORBIDDEN);
        assertThatThrownBy(() -> useCase.closeByOrder(ORDER_GROUP_ID, null))
                .isInstanceOf(BusinessException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CHARGING);
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.REQUESTED);
    }

    @Test
    @DisplayName("주문 그룹 삭제 이벤트로 닫으면 소유자를 확인하지 않고 삭제 사유를 남긴다")
    void closeOnOrderDeleted() {
        Charge charge = openCardWindow();

        PaymentCloseResultDto result = useCase.closeOnOrderDeleted(ORDER_GROUP_ID);

        assertThat(result.status()).isEqualTo(PaymentCloseResultDto.Status.CLOSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentCloseReason.ORDER_DELETED.message());
        assertThat(charge.getFailureCode()).isEqualTo(PaymentCloseReason.ORDER_DELETED.code());
    }
}
