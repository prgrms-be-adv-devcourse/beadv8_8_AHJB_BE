package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WalletType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_POINT;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_SETTLEMENT;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INVALID_AMOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class WalletTest {

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet(null, WalletType.USER);
    }

    @Test
    @DisplayName("새 지갑의 포인트는 0")
    void newWalletHasNoPoint() {
        assertThat(wallet.getPointBalance()).isZero();
        assertThat(wallet.toDto().pointBalance()).isZero();
    }

    @Test
    @DisplayName("포인트 입금 · 차감은 포인트만 바꾸고 예치금 · 정산금은 그대로 둔다")
    void pointChangesOnlyPointPocket() {
        wallet.deposit(10_000);
        wallet.depositSettlement(7_000);

        wallet.depositPoint(1_500);
        wallet.usePoint(400);

        assertThat(wallet.getPointBalance()).isEqualTo(1_100);
        assertThat(wallet.getBalance()).isEqualTo(10_000);
        assertThat(wallet.getSettlementBalance()).isEqualTo(7_000);
    }

    @Test
    @DisplayName("예치금 입금 · 차감은 포인트를 바꾸지 않는다")
    void depositChangesDoNotTouchPoint() {
        wallet.depositPoint(1_500);

        wallet.deposit(10_000);
        wallet.withdrawal(10_000);

        assertThat(wallet.getPointBalance()).isEqualTo(1_500);
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("정산금 차감은 정산금만 바꾸고 예치금 · 포인트는 그대로 둔다")
    void settlementWithdrawChangesOnlySettlementPocket() {
        wallet.deposit(10_000);
        wallet.depositSettlement(7_000);
        wallet.depositPoint(1_500);

        wallet.withdrawSettlement(7_000);

        assertThat(wallet.getSettlementBalance()).isZero();
        assertThat(wallet.getBalance()).isEqualTo(10_000);
        assertThat(wallet.getPointBalance()).isEqualTo(1_500);
    }

    @Test
    @DisplayName("가진 정산금보다 많이 차감하면 INSUFFICIENT_SETTLEMENT. 예치금이 충분해도 대신 쓰지 않는다")
    void withdrawMoreThanSettlement() {
        wallet.deposit(10_000);
        wallet.depositSettlement(300);

        assertThatThrownBy(() -> wallet.withdrawSettlement(301))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(INSUFFICIENT_SETTLEMENT);

        assertThat(wallet.getSettlementBalance()).isEqualTo(300);
        assertThat(wallet.getBalance()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("주머니별 잔액 조회는 주머니마다 자기 잔액을 돌려준다")
    void balanceOfEachPocket() {
        wallet.deposit(10_000);
        wallet.depositSettlement(7_000);
        wallet.depositPoint(1_500);

        assertThat(wallet.getBalanceOf(WalletAccount.DEPOSIT)).isEqualTo(10_000);
        assertThat(wallet.getBalanceOf(WalletAccount.SETTLEMENT)).isEqualTo(7_000);
        assertThat(wallet.getBalanceOf(WalletAccount.POINT)).isEqualTo(1_500);
    }

    @Test
    @DisplayName("가진 포인트보다 많이 차감하면 INSUFFICIENT_POINT. 예치금이 충분해도 대신 쓰지 않는다")
    void withdrawMoreThanPoint() {
        wallet.deposit(10_000);
        wallet.depositPoint(300);

        assertThatThrownBy(() -> wallet.usePoint(301))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(INSUFFICIENT_POINT);

        assertThat(wallet.getPointBalance()).isEqualTo(300);
        assertThat(wallet.getBalance()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("가진 포인트를 전부 차감할 수 있다")
    void withdrawAllPoint() {
        wallet.depositPoint(300);

        wallet.usePoint(300);

        assertThat(wallet.getPointBalance()).isZero();
        assertThat(wallet.hasEnoughPoint(1)).isFalse();
    }

    @Test
    @DisplayName("0 이하 금액은 포인트 입금 · 차감 모두 INVALID_AMOUNT")
    void nonPositiveAmount() {
        assertThatThrownBy(() -> wallet.depositPoint(0))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(INVALID_AMOUNT);
        assertThatThrownBy(() -> wallet.depositPoint(-1))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(INVALID_AMOUNT);
        assertThatThrownBy(() -> wallet.usePoint(0))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(INVALID_AMOUNT);
    }

    @Test
    @DisplayName("포인트 보유 여부는 포인트 잔액으로만 판단한다")
    void hasEnoughPoint() {
        wallet.deposit(10_000);
        wallet.depositPoint(300);

        assertThat(wallet.hasEnoughPoint(300)).isTrue();
        assertThat(wallet.hasEnoughPoint(301)).isFalse();
    }
}
