package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.PaymentRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletRepository;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.INSUFFICIENT_BALANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 예치금 선입선출 소비 규칙.
 *   잔액 = 환불금 풀 + Σ(CHARGED 충전의 remainingAmount)
 *   차감 순서: 이 결제용 충전(earmarked) → 환불금 풀 → 오래된 충전부터
 */
@SpringBootTest
@Transactional
class DepositConsumerTest {

    private static final Long HOLDER_ID = 9_101L;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Autowired DepositConsumer depositConsumer;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired PaymentRepository paymentRepository;

    @MockitoBean SettlementEventListener settlementEventListener;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        WalletMember member = walletMemberRepository.save(new WalletMember(HOLDER_ID, UUID.randomUUID()));
        wallet = walletRepository.save(Wallet.createUserWallet(member));
    }

    /** 충전 완료(CHARGED) 건 하나를 만들고 지갑에도 입금한다. chargedAt 으로 선입선출 순서를 정한다 */
    private Charge charged(long amount, LocalDateTime chargedAt) {
        Charge charge = Charge.requestManual(wallet, HOLDER_ID, amount);
        charge.markCharged("pk-" + UUID.randomUUID(), chargedAt, chargedAt);
        wallet.deposit(amount);
        return chargeRepository.save(charge);
    }

    /** 환불 등 충전이 아닌 입금 */
    private void refunded(long amount) {
        wallet.deposit(amount);
    }

    @Test
    @DisplayName("환불금이 있으면 환불금부터 쓰고 충전 건은 건드리지 않는다")
    void refundPoolFirst() {
        Charge c1 = charged(10_000, T0);
        refunded(5_000);                                  // 잔액 15_000 = 환불 5_000 + C1 10_000

        depositConsumer.consume(wallet, 5_000);

        assertThat(c1.getRemainingAmount()).isEqualTo(10_000);
        assertThat(c1.isCancelable()).isTrue();
    }

    @Test
    @DisplayName("환불금을 넘는 금액은 오래된 충전부터 소비된다")
    void refundPoolThenOldestCharge() {
        Charge c1 = charged(10_000, T0);
        Charge c2 = charged(10_000, T0.plusDays(1));
        refunded(3_000);                                  // 잔액 23_000

        depositConsumer.consume(wallet, 8_000);           // 환불 3_000 → C1 5_000

        assertThat(c1.getRemainingAmount()).isEqualTo(5_000);
        assertThat(c1.getUsedAmount()).isEqualTo(5_000);
        assertThat(c2.getRemainingAmount()).isEqualTo(10_000);
        assertThat(c1.isCancelable()).isFalse();          // 일부라도 쓰이면 취소 불가
        assertThat(c2.isCancelable()).isTrue();
    }

    @Test
    @DisplayName("충전 건이 여러 개면 chargedAt 순으로 소진된다 (선입선출)")
    void fifoAcrossCharges() {
        Charge old = charged(10_000, T0);
        Charge mid = charged(10_000, T0.plusDays(1));
        Charge recent = charged(10_000, T0.plusDays(2));  // 잔액 30_000

        depositConsumer.consume(wallet, 12_000);

        assertThat(old.getRemainingAmount()).isZero();
        assertThat(mid.getRemainingAmount()).isEqualTo(8_000);
        assertThat(recent.getRemainingAmount()).isEqualTo(10_000);
        assertThat(recent.isCancelable()).isTrue();
    }

    @Test
    @DisplayName("이 결제를 위해 충전한 건(earmarked)은 환불금·오래된 충전보다 먼저 전액 소비된다")
    void earmarkedFirst() {
        Charge old = charged(10_000, T0);
        refunded(2_000);

        Payment payment = paymentRepository.save(Payment.create(8_001L, HOLDER_ID, wallet, 17_000));
        payment.decideCharge(wallet.getBalance());        // 부족분 5_000
        Charge forPayment = Charge.requestForPayment(wallet, payment);
        forPayment.markCharged("pk-fp", T0.plusDays(3), T0.plusDays(3));
        wallet.deposit(5_000);                            // 잔액 17_000
        forPayment = chargeRepository.save(forPayment);

        depositConsumer.consume(wallet, 17_000, forPayment);

        assertThat(forPayment.getRemainingAmount()).isZero();   // ① 먼저 전액
        assertThat(old.getRemainingAmount()).isZero();          // ② 환불 2_000 ③ old 10_000
    }

    @Test
    @DisplayName("이미 쓰인 충전은 환불이 돌아와도 미사용으로 되돌아가지 않는다")
    void usedChargeStaysUsedAfterRefund() {
        Charge c1 = charged(10_000, T0);
        depositConsumer.consume(wallet, 10_000);
        wallet.withdrawal(10_000);                        // 결제로 빠져나감

        refunded(10_000);                                 // 환불로 돌아옴 → 환불금 풀

        assertThat(c1.getRemainingAmount()).isZero();
        assertThat(c1.isCancelable()).isFalse();
        depositConsumer.consume(wallet, 10_000);          // 환불금만으로 처리되고 예외 없음
    }

    @Test
    @DisplayName("잔액보다 많은 금액을 소비하려 하면 INSUFFICIENT_BALANCE")
    void moreThanBalance() {
        charged(5_000, T0);

        assertThatThrownBy(() -> depositConsumer.consume(wallet, 6_000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("0 이하 금액은 아무 것도 하지 않는다")
    void zeroAmount() {
        Charge c1 = charged(5_000, T0);

        depositConsumer.consume(wallet, 0);

        assertThat(c1.getRemainingAmount()).isEqualTo(5_000);
    }
}
