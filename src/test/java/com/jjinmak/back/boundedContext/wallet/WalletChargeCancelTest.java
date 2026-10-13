package com.jjinmak.back.boundedContext.wallet;

import com.jjinmak.back.boundedContext.wallet.app.WalletFacade;
import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import com.jjinmak.back.boundedContext.wallet.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.wallet.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletTransactionRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import com.jjinmak.back.shared.wallet.port.WalletPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 충전 취소 (WalletChargeCancelPort → WalletFacade.cancelCharge / revertCancelCharge) 검증.
 *
 * 결제 컨텍스트가 호출하는 방식 그대로 포트 인터페이스를 통해 호출한다.
 * 모든 케이스는 "충전 10,000원이 먼저 되어 있는" 상태에서 시작한다.
 */
@SpringBootTest
class WalletChargeCancelTest {

    private static final long CHARGE_AMOUNT = 10_000L;
    private static final Long PAYMENT_ID = 777L;

    @Autowired
    private WalletPort walletPort;

    @Autowired
    private WalletFacade walletFacade;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletMemberRepository walletMemberRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long memberId;
    private Long walletId;

    @BeforeEach
    void setUp() {
        memberId = ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        walletId = walletRepository.save(Wallet.createUserWallet(member)).getId();

        // 충전이 먼저 되어 있어야 취소할 수 있다
        walletFacade.charge(new ChargeDto(memberId, CHARGE_AMOUNT, PAYMENT_ID));
    }

    @AfterEach
    void tearDown() {
        walletTransactionRepository.deleteAll(transactionsOfWallet());
        walletRepository.deleteById(walletId);
        walletMemberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("충전 취소에 성공하면 잔액이 차감되고 원장에 CHARGE_CANCEL (-) 한 줄이 기록된다")
    void cancelCharge_success() {
        // when
        walletPort.cancelCharge(new ChargeDto(memberId, CHARGE_AMOUNT, PAYMENT_ID));

        // then
        assertThat(balance()).isZero();

        WalletTransaction cancel = findTransaction(WalletTransactionType.CHARGE_CANCEL);
        assertThat(cancel.getAmount()).isEqualTo(-CHARGE_AMOUNT);
        assertThat(cancel.getBalanceAfter()).isZero();
        assertThat(cancel.getReferenceId()).isEqualTo(PAYMENT_ID);
        assertThat(cancel.getIdempotencyKey()).isEqualTo("PAYMENT:" + PAYMENT_ID + ":CHARGE_CANCEL");
        assertThat(transactionsOfWallet()).hasSize(2); // CHARGE + CHARGE_CANCEL
    }

    @Test
    @DisplayName("같은 결제에 대해 취소를 두 번 호출해도 두 번째는 아무 일도 하지 않는다 (멱등)")
    void cancelCharge_isIdempotent() {
        ChargeDto dto = new ChargeDto(memberId, CHARGE_AMOUNT, PAYMENT_ID);

        walletPort.cancelCharge(dto);
        walletPort.cancelCharge(dto);

        assertThat(balance()).isZero();
        assertThat(transactionsOfWallet()).hasSize(2);
    }

    @Test
    @DisplayName("충전금을 이미 써서 잔액이 부족하면 INSUFFICIENT_BALANCE 로 실패하고 아무것도 변하지 않는다")
    void cancelCharge_insufficientBalance_fails() {
        // given: 충전금 중 3,000원을 써버린 상태
        spend(3_000L);

        // when & then
        assertThatThrownBy(() ->
                walletPort.cancelCharge(new ChargeDto(memberId, CHARGE_AMOUNT, PAYMENT_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(INSUFFICIENT_BALANCE);

        assertThat(balance()).isEqualTo(CHARGE_AMOUNT - 3_000L);
        assertThat(transactionsOfWallet()).hasSize(1); // CHARGE 만
    }

    @Test
    @DisplayName("해당 결제의 CHARGE 기록이 없으면 TRANSACTION_NOT_FOUND 로 실패한다")
    void cancelCharge_unknownPayment_fails() {
        Long unknownPaymentId = PAYMENT_ID + 1;

        assertThatThrownBy(() ->
                walletPort.cancelCharge(new ChargeDto(memberId, CHARGE_AMOUNT, unknownPaymentId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TRANSACTION_NOT_FOUND);

        assertThat(balance()).isEqualTo(CHARGE_AMOUNT);
        assertThat(transactionsOfWallet()).hasSize(1);
    }

    @Test
    @DisplayName("요청 금액이 원장의 CHARGE 금액과 다르면 INVALID_AMOUNT 로 실패한다")
    void cancelCharge_amountMismatch_fails() {
        assertThatThrownBy(() ->
                walletPort.cancelCharge(new ChargeDto(memberId, CHARGE_AMOUNT - 1, PAYMENT_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(INVALID_AMOUNT);

        assertThat(balance()).isEqualTo(CHARGE_AMOUNT);
        assertThat(transactionsOfWallet()).hasSize(1);
    }

    @Test
    @DisplayName("취소 후 보상(revert)을 호출하면 잔액이 복구되고 원장에 REVERSAL (+) 이 기록된다")
    void revertCancelCharge_afterCancel_restoresBalance() {
        ChargeDto dto = new ChargeDto(memberId, CHARGE_AMOUNT, PAYMENT_ID);
        walletPort.cancelCharge(dto);

        // when: PG 취소 실패를 가정하고 보상
        walletPort.revertCancelCharge(dto);

        // then
        assertThat(balance()).isEqualTo(CHARGE_AMOUNT);

        WalletTransaction reversal = findTransaction(WalletTransactionType.REVERSAL);
        assertThat(reversal.getAmount()).isEqualTo(CHARGE_AMOUNT);
        assertThat(reversal.getBalanceAfter()).isEqualTo(CHARGE_AMOUNT);
        assertThat(transactionsOfWallet()).hasSize(3); // CHARGE + CHARGE_CANCEL + REVERSAL
    }

    @Test
    @DisplayName("취소된 적 없는 결제에 보상을 호출하면 아무 일도 하지 않는다")
    void revertCancelCharge_withoutCancel_doesNothing() {
        walletPort.revertCancelCharge(new ChargeDto(memberId, CHARGE_AMOUNT, PAYMENT_ID));

        assertThat(balance()).isEqualTo(CHARGE_AMOUNT);
        assertThat(transactionsOfWallet()).hasSize(1);
    }

    @Test
    @DisplayName("보상을 두 번 호출해도 두 번째는 아무 일도 하지 않는다 (멱등)")
    void revertCancelCharge_isIdempotent() {
        ChargeDto dto = new ChargeDto(memberId, CHARGE_AMOUNT, PAYMENT_ID);
        walletPort.cancelCharge(dto);

        walletPort.revertCancelCharge(dto);
        walletPort.revertCancelCharge(dto);

        assertThat(balance()).isEqualTo(CHARGE_AMOUNT);
        assertThat(transactionsOfWallet()).hasSize(3);
    }

    // ===== helpers =====

    /** 사용자가 충전금 일부를 쓴 상황을 만든다. (결제/홀딩 유스케이스가 아직 없어 잔액만 직접 내린다) */
    private void spend(long amount) {
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = walletRepository.findById(walletId).orElseThrow();
            wallet.withdrawal(amount);
        });
    }

    private long balance() {
        return walletRepository.findById(walletId).orElseThrow().getBalance();
    }

    private WalletTransaction findTransaction(WalletTransactionType type) {
        return transactionsOfWallet().stream()
                .filter(tx -> tx.getType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError(type + " 원장 기록이 없습니다"));
    }

    private List<WalletTransaction> transactionsOfWallet() {
        return walletTransactionRepository.findAll().stream()
                .filter(tx -> walletId.equals(tx.getWallet().getId()))
                .toList();
    }
}
