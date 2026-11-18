package com.jjinmak.back.boundedContext.payment.domain;

import com.jjinmak.back.boundedContext.payment.domain.enums.WalletAccount;
import com.jjinmak.back.boundedContext.payment.domain.enums.WithdrawalStatus;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.INVALID_AMOUNT;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.INVALID_WITHDRAWAL_STATUS;

/**
 * 출금 1건: 사용자가 보유 예치금 전액을 등록된 계좌로 환급받은 기록.
 *
 * 현재는 실제 송금이 없는 명목상 기능이다. 요청 즉시 지갑 잔액을 0 으로 만들고 원장(WITHDRAWAL)과 이 기록을 남겨
 * "계좌로 빠져나갔다" 는 사실만 보여준다. 계좌 송금이 연동되면 status 가 REQUESTED 로 시작해 송금 결과에 따라 바뀐다.
 *
 * - account : 어느 주머니에서 나갔는지. 지금은 예치금(DEPOSIT)만. 추후 정산금(SETTLEMENT) 송금도 같은 엔티티로 처리
 * - 출금은 예치금 전부를 쓰는 것이므로 선입선출상 모든 충전 건의 미사용 금액이 0 이 되고, 이후 충전 취소는 불가능하다
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

    /** 회원 id (= WalletMember id) */
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

    /** 예치금 출금 요청 */
    public static Withdrawal requestDeposit(Wallet wallet, Long holderId, long amount) {
        return new Withdrawal(wallet, holderId, amount, WalletAccount.DEPOSIT);
    }

    /** 지갑 차감 + 원장 기록이 끝난 뒤 호출. 지금은 송금이 없으므로 같은 트랜잭션에서 바로 완료 처리한다 */
    public void complete(LocalDateTime completedAt) {
        if (status == WithdrawalStatus.COMPLETED) return;
        requireRequested();
        this.status = WithdrawalStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    /** 송금 실패 (계좌 송금 연동 후 사용) */
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
