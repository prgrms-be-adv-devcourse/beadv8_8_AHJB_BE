package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

import static com.jjinmak.back.payment.exception.WalletErrorCode.INVALID_AMOUNT;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INVALID_WITHDRAWAL_STATUS;

/**
 * 출금 1건: 사용자가 보유 예치금 또는 정산금 전액을 등록된 계좌로 받은 기록.
 *
 * 현재는 실제 송금이 없는 명목상 기능이다. 요청 즉시 해당 주머니 잔액을 0 으로 만들고 원장(WITHDRAWAL / SETTLEMENT_WITHDRAWAL)과
 * 이 기록을 남겨 "계좌로 빠져나갔다" 는 사실만 보여준다. 계좌 송금이 연동되면 status 가 REQUESTED 로 시작해 송금 결과에 따라 바뀐다.
 *
 * - account : 어느 주머니에서 나갔는지. 예치금(DEPOSIT) 또는 정산금(SETTLEMENT). 포인트는 출금할 수 없다
 * - 예치금 출금은 예치금 전부를 쓰는 것이므로 선입선출상 모든 충전 건의 미사용 금액이 0 이 되고, 이후 충전 취소는 불가능하다
 * - 정산금 출금은 충전 건과 관계없다
 * - 계좌 정보는 회원 컨텍스트 소관이라 여기에 두지 않는다
 */
@Entity
@Table(
        name = "withdrawal",
        indexes = @Index(name = "idx_withdrawal_holder", columnList = "holder_id, created_at")
)
@Getter
@NoArgsConstructor
public class Withdrawal extends BaseIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Wallet wallet;

    @Column(nullable = false)
    private Long holderId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WalletAccount account;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WithdrawalStatus status;

    private LocalDateTime completedAt;

    private LocalDateTime failedAt;

    @Column(length = 255)
    private String failureReason;


    private Withdrawal(Wallet wallet, Long holderId, long amount, WalletAccount account) {
        if (amount <= 0) {
            throw new BusinessException(INVALID_AMOUNT);
        }
        this.wallet = wallet;
        this.holderId = holderId;
        this.amount = amount;
        this.account = account;
        this.status = WithdrawalStatus.REQUESTED;
    }

    public static Withdrawal requestDeposit(Wallet wallet, Long holderId, long amount) {
        return new Withdrawal(wallet, holderId, amount, WalletAccount.DEPOSIT);
    }

    public static Withdrawal requestSettlement(Wallet wallet, Long holderId, long amount) {
        return new Withdrawal(wallet, holderId, amount, WalletAccount.SETTLEMENT);
    }

    public void complete(LocalDateTime completedAt) {
        if (status == WithdrawalStatus.COMPLETED) return;
        requireRequested();
        this.status = WithdrawalStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    // 송금 실패 (계좌 송금 연동 후 사용)
    public void fail(LocalDateTime failedAt, String reason) {
        if (status == WithdrawalStatus.FAILED) return;
        requireRequested();
        this.status = WithdrawalStatus.FAILED;
        this.failedAt = failedAt;
        this.failureReason = reason;
    }

    private void requireRequested() {
        if (status != WithdrawalStatus.REQUESTED) {
            throw new BusinessException(INVALID_WITHDRAWAL_STATUS);
        }
    }
}
