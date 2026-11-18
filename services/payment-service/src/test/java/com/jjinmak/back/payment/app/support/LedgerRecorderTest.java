package com.jjinmak.back.payment.app.support;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.back.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_EARN;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_HOLD_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_HOLD_RECEIVE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_REFUND_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_REFUND_RECEIVE;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_BALANCE;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_POINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 포인트 원장 기록. 잔액 변경과 원장 기록이 함께 일어나고, 주머니 · 부호 · 기록 직후 잔액 · 그룹이 맞게 남는지 본다.
 */
class LedgerRecorderTest {

    private static final long ORDER_ID = 101L;
    private static final String GROUP_ID = "3f2a9c1e-0000-4000-8000-000000000001";

    private LedgerRecorder recorder;
    private final List<WalletTransaction> saved = new ArrayList<>();

    private Wallet buyer;
    private Wallet escrow;

    @BeforeEach
    void setUp() {
        WalletTransactionRepository repository = mock(WalletTransactionRepository.class);
        when(repository.save(any(WalletTransaction.class))).thenAnswer(invocation -> {
            WalletTransaction row = invocation.getArgument(0);
            saved.add(row);
            return row;
        });
        recorder = new LedgerRecorder(repository);

        buyer = new Wallet(null, WalletType.USER);
        escrow = Wallet.createSystemWallet(WalletType.ESCROW);
    }

    @Test
    @DisplayName("포인트 적립은 포인트 주머니에 + 한 줄만 남기고 예치금은 그대로 둔다")
    void earnPoint() {
        buyer.deposit(10_000);

        recorder.depositPoint(buyer, POINT_EARN, 300, ORDER, ORDER_ID);

        assertThat(buyer.getPointBalance()).isEqualTo(300);
        assertThat(buyer.getBalance()).isEqualTo(10_000);

        assertThat(saved).hasSize(1);
        WalletTransaction row = saved.get(0);
        assertThat(row.getWallet()).isSameAs(buyer);
        assertThat(row.getAccount()).isEqualTo(WalletAccount.POINT);
        assertThat(row.getType()).isEqualTo(POINT_EARN);
        assertThat(row.getAmount()).isEqualTo(300);
        assertThat(row.getBalanceAfter()).isEqualTo(300);                 // 포인트 주머니의 잔액
        assertThat(row.getIdempotencyKey()).isEqualTo("ORDER:101:POINT_EARN");
    }

    @Test
    @DisplayName("포인트 적립을 이어서 하면 기록 직후 잔액은 포인트 누계다")
    void earnPointTwice() {
        recorder.depositPoint(buyer, POINT_EARN, 300, ORDER, 101L);
        recorder.depositPoint(buyer, POINT_EARN, 200, ORDER, 102L);

        assertThat(saved).extracting(WalletTransaction::getBalanceAfter).containsExactly(300L, 500L);
        assertThat(saved.get(0).getTransactionGroupId()).isNotEqualTo(saved.get(1).getTransactionGroupId());
    }

    @Test
    @DisplayName("포인트 결제는 구매자 포인트(-)와 ESCROW 예치금(+)을 같은 그룹 두 줄로 남기고 합이 0 이다")
    void holdWithPoint() {
        buyer.deposit(10_000);
        buyer.depositPoint(1_500);

        recorder.transferPointToDeposit(buyer, POINT_HOLD_PAY, escrow, POINT_HOLD_RECEIVE, 1_000, GROUP_ID, ORDER, ORDER_ID);

        assertThat(buyer.getPointBalance()).isEqualTo(500);
        assertThat(buyer.getBalance()).isEqualTo(10_000);                // 구매자 예치금은 쓰이지 않는다
        assertThat(escrow.getBalance()).isEqualTo(1_000);                // ESCROW 는 예치금으로 보관한다
        assertThat(escrow.getPointBalance()).isZero();

        assertThat(saved).hasSize(2);
        WalletTransaction pay = saved.get(0);
        assertThat(pay.getWallet()).isSameAs(buyer);
        assertThat(pay.getAccount()).isEqualTo(WalletAccount.POINT);
        assertThat(pay.getType()).isEqualTo(POINT_HOLD_PAY);
        assertThat(pay.getAmount()).isEqualTo(-1_000);
        assertThat(pay.getBalanceAfter()).isEqualTo(500);

        WalletTransaction receive = saved.get(1);
        assertThat(receive.getWallet()).isSameAs(escrow);
        assertThat(receive.getAccount()).isEqualTo(WalletAccount.DEPOSIT);
        assertThat(receive.getType()).isEqualTo(POINT_HOLD_RECEIVE);
        assertThat(receive.getAmount()).isEqualTo(1_000);
        assertThat(receive.getBalanceAfter()).isEqualTo(1_000);

        assertThat(saved).extracting(WalletTransaction::getTransactionGroupId).containsOnly(GROUP_ID);
        assertThat(saved.stream().mapToLong(WalletTransaction::getAmount).sum()).isZero();
    }

    @Test
    @DisplayName("포인트 환불은 ESCROW 예치금(-)과 구매자 포인트(+)를 같은 그룹 두 줄로 남기고 합이 0 이다")
    void refundToPoint() {
        buyer.depositPoint(1_500);
        recorder.transferPointToDeposit(buyer, POINT_HOLD_PAY, escrow, POINT_HOLD_RECEIVE, 1_000, GROUP_ID, ORDER, ORDER_ID);
        saved.clear();

        String refundGroupId = "3f2a9c1e-0000-4000-8000-000000000002";
        recorder.transferDepositToPoint(escrow, POINT_REFUND_PAY, buyer, POINT_REFUND_RECEIVE, 1_000, refundGroupId, ORDER, ORDER_ID);

        assertThat(buyer.getPointBalance()).isEqualTo(1_500);            // 쓴 포인트가 포인트로 돌아온다
        assertThat(buyer.getBalance()).isZero();                         // 예치금으로 돌아오지 않는다
        assertThat(escrow.getBalance()).isZero();

        assertThat(saved).hasSize(2);
        WalletTransaction pay = saved.get(0);
        assertThat(pay.getWallet()).isSameAs(escrow);
        assertThat(pay.getAccount()).isEqualTo(WalletAccount.DEPOSIT);
        assertThat(pay.getType()).isEqualTo(POINT_REFUND_PAY);
        assertThat(pay.getAmount()).isEqualTo(-1_000);
        assertThat(pay.getBalanceAfter()).isZero();

        WalletTransaction receive = saved.get(1);
        assertThat(receive.getWallet()).isSameAs(buyer);
        assertThat(receive.getAccount()).isEqualTo(WalletAccount.POINT);
        assertThat(receive.getType()).isEqualTo(POINT_REFUND_RECEIVE);
        assertThat(receive.getAmount()).isEqualTo(1_000);
        assertThat(receive.getBalanceAfter()).isEqualTo(1_500);

        assertThat(saved).extracting(WalletTransaction::getTransactionGroupId).containsOnly(refundGroupId);
        assertThat(saved.stream().mapToLong(WalletTransaction::getAmount).sum()).isZero();
    }

    @Test
    @DisplayName("포인트가 모자라면 INSUFFICIENT_POINT 로 끝나고 잔액도 원장도 바뀌지 않는다")
    void holdWithInsufficientPoint() {
        buyer.deposit(10_000);
        buyer.depositPoint(500);

        assertThatThrownBy(() -> recorder.transferPointToDeposit(
                buyer, POINT_HOLD_PAY, escrow, POINT_HOLD_RECEIVE, 501, GROUP_ID, ORDER, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(INSUFFICIENT_POINT);

        assertThat(buyer.getPointBalance()).isEqualTo(500);
        assertThat(buyer.getBalance()).isEqualTo(10_000);
        assertThat(escrow.getBalance()).isZero();
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("ESCROW 잔액이 모자라면 포인트 환불은 INSUFFICIENT_BALANCE 로 끝나고 포인트가 늘지 않는다")
    void refundWithInsufficientEscrow() {
        assertThatThrownBy(() -> recorder.transferDepositToPoint(
                escrow, POINT_REFUND_PAY, buyer, POINT_REFUND_RECEIVE, 1_000, GROUP_ID, ORDER, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(INSUFFICIENT_BALANCE);

        assertThat(buyer.getPointBalance()).isZero();
        assertThat(saved).isEmpty();
    }
}
