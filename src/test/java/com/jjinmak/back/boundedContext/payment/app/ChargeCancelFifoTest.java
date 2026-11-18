package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.boundedContext.payment.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.TossPaymentsClient;
import com.jjinmak.back.boundedContext.payment.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletTransactionRepository;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.CHARGE_NOT_CANCELABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 선입선출이 실제 결제·충전 취소 흐름에 적용되는지 (PaymentFacade 경유).
 * 토스 호출은 모킹한다.
 */
@SpringBootTest
@Transactional
class ChargeCancelFifoTest {

    private static final Long BUYER_ID = 9_201L;
    private static final Long SELLER_ID = 9_202L;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Autowired PaymentFacade paymentFacade;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired WalletTransactionRepository walletTransactionRepository;

    @MockitoBean TossPaymentsClient toss;
    @MockitoBean SettlementEventListener settlementEventListener;

    private Wallet buyerWallet;

    @BeforeEach
    void setUp() {
        buyerWallet = walletOf(BUYER_ID);
        walletOf(SELLER_ID);
    }

    private Wallet walletOf(Long memberId) {
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        return walletRepository.save(Wallet.createUserWallet(member));
    }

    /** 수동 충전 완료 건 + 지갑 입금 + 원장 CHARGE (취소 유스케이스가 원장을 대조하므로 함께 만든다) */
    private Charge charged(long amount, LocalDateTime chargedAt) {
        Charge charge = Charge.requestManual(buyerWallet, BUYER_ID, amount);
        charge.markCharged("pk-" + UUID.randomUUID(), chargedAt, chargedAt);
        charge = chargeRepository.save(charge);
        buyerWallet.deposit(amount);
        walletTransactionRepository.save(WalletTransaction.of(
                buyerWallet, WalletTransactionType.CHARGE, amount, UUID.randomUUID().toString(), ReferenceType.PAYMENT, charge.getId()));
        return charge;
    }

    private PaymentResultDto payWithWallet(long orderGroupId, long amount) {
        return paymentFacade.requestPayment(new PaymentRequestDto(orderGroupId, BUYER_ID, amount,
                List.of(new PaymentRequestDto.Item(orderGroupId * 10, SELLER_ID, amount, 0L))));
    }

    @Test
    @DisplayName("한 푼도 안 쓴 충전은 취소되고 예치금이 그만큼 줄어든다")
    void cancelUntouched() {
        Charge c1 = charged(10_000, T0);

        ChargeSummaryDto result = paymentFacade.cancelCharge(BUYER_ID, c1.getId());

        assertThat(result.status()).isEqualTo(ChargeStatus.CANCELED);
        assertThat(result.remainingAmount()).isZero();
        assertThat(buyerWallet.getBalance()).isZero();
        verify(toss).cancel(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("결제로 일부라도 쓰인 충전은 취소할 수 없다 (토스도 호출하지 않는다)")
    void cannotCancelPartiallyUsed() {
        Charge c1 = charged(10_000, T0);

        assertThat(payWithWallet(1L, 3_000).isPaid()).isTrue();      // C1 에서 3_000 소비

        assertThat(c1.getRemainingAmount()).isEqualTo(7_000);
        assertThatThrownBy(() -> paymentFacade.cancelCharge(BUYER_ID, c1.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CHARGE_NOT_CANCELABLE);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("충전이 여러 개면 결제는 오래된 충전부터 쓰므로 최근 충전은 여전히 취소할 수 있다")
    void paymentConsumesOldestSoRecentStaysCancelable() {
        Charge old = charged(10_000, T0);
        Charge recent = charged(10_000, T0.plusDays(1));

        payWithWallet(2L, 10_000);                                      // old 전액 소비

        assertThat(old.getRemainingAmount()).isZero();
        assertThat(recent.isCancelable()).isTrue();

        ChargeSummaryDto result = paymentFacade.cancelCharge(BUYER_ID, recent.getId());
        assertThat(result.status()).isEqualTo(ChargeStatus.CANCELED);
        assertThat(buyerWallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("환불로 돌아온 돈이 있으면 결제는 환불금부터 쓰므로 충전은 취소 가능하게 남는다")
    void refundMoneyConsumedBeforeCharge() {
        Charge c1 = charged(10_000, T0);
        buyerWallet.deposit(4_000);                                     // 환불금 (충전 아님)

        payWithWallet(3L, 4_000);

        assertThat(c1.getRemainingAmount()).isEqualTo(10_000);
        assertThat(c1.isCancelable()).isTrue();
    }

    @Test
    @DisplayName("충전 취소 후 예치금 잔액 == 환불금 + Σ미사용 금액 불변식이 유지된다")
    void invariantHolds() {
        Charge c1 = charged(10_000, T0);
        Charge c2 = charged(5_000, T0.plusDays(1));
        buyerWallet.deposit(2_000);                                     // 환불금

        payWithWallet(4L, 6_000);                                       // 환불 2_000 → C1 4_000
        paymentFacade.cancelCharge(BUYER_ID, c2.getId());               // C2 전액 미사용 → 취소

        long remainingSum = c1.getRemainingAmount() + c2.getRemainingAmount();   // 6_000 + 0
        assertThat(buyerWallet.getBalance()).isEqualTo(remainingSum);            // 환불금 풀 0
    }
}
