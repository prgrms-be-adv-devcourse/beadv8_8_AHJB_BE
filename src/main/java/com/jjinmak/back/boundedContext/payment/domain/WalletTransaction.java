package com.jjinmak.back.boundedContext.payment.domain;


import com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletAccount;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.INVALID_TRANSACTION_AMOUNT;

/**
 * 원장. 지갑 잔액이 바뀔 때마다 한 줄씩 append-only로 기록한다. 수정·삭제하지 않는다.
 *
 * - account       : 어느 주머니(예치금 / 정산금)의 변동인지
 * - amount        : 부호 있는 금액. 입금 +, 출금 -
 * - balanceAfter  : 이 기록 직후의 해당 주머니 잔액 (대사용 스냅샷)
 * - transactionGroupId : 한 번의 비즈니스 거래로 묶이는 기록들의 공통 id.
 *                        HOLD 한 건이면 구매자(-)와 ESCROW(+) 두 줄이 같은 id를 가진다.
 * - idempotencyKey: 같은 요청이 두 번 들어와도 한 번만 기록되도록 하는 키
 */
@Entity
@Table(
        name = "wallet_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallet_tx_idempotency", columnNames = "idempotency_key"),
        indexes = {
                @Index(name = "idx_wallet_tx_wallet", columnList = "wallet_id, created_at"),
                @Index(name = "idx_wallet_tx_wallet_account", columnList = "wallet_id, account"),
                @Index(name = "idx_wallet_tx_group", columnList = "transaction_group_id"),
                @Index(name = "idx_wallet_tx_reference", columnList = "reference_type, reference_id")
        }
)
@Getter
@NoArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private WalletTransactionType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WalletAccount account;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long balanceAfter;

    @Column(nullable = false, length = 36)
    private String transactionGroupId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ReferenceType referenceType;

    @Column(nullable = false)
    private Long referenceId;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;


    private WalletTransaction(Wallet wallet,
                              WalletAccount account,
                              WalletTransactionType type,
                              long amount,
                              String transactionGroupId,
                              ReferenceType referenceType,
                              Long referenceId,
                              String idempotencyKey) {
        if (amount == 0) {
            throw new BusinessException(INVALID_TRANSACTION_AMOUNT);
        }
        this.wallet = wallet;
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = wallet.getBalanceOf(account);
        this.transactionGroupId = transactionGroupId;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 예치금(DEPOSIT) 변동 기록. 지갑 잔액을 먼저 변경한 뒤 호출한다.
     */
    public static WalletTransaction of(Wallet wallet,
                                       WalletTransactionType type,
                                       long signedAmount,
                                       String transactionGroupId,
                                       ReferenceType referenceType,
                                       Long referenceId) {
        return of(wallet, WalletAccount.DEPOSIT, type, signedAmount, transactionGroupId, referenceType, referenceId);
    }

    /**
     * 주머니를 지정한 변동 기록. 지갑 잔액을 먼저 변경한 뒤 호출한다.
     * balanceAfter 는 호출 시점의 해당 주머니 잔액을 그대로 저장한다.
     */
    public static WalletTransaction of(Wallet wallet,
                                       WalletAccount account,
                                       WalletTransactionType type,
                                       long signedAmount,
                                       String transactionGroupId,
                                       ReferenceType referenceType,
                                       Long referenceId) {
        return new WalletTransaction(wallet, account, type, signedAmount, transactionGroupId,
                referenceType, referenceId, idempotencyKeyOf(referenceType, referenceId, type));
    }

    public static String idempotencyKeyOf(ReferenceType referenceType, Long referenceId, WalletTransactionType type) {
        return referenceType + ":" + referenceId + ":" + type;
    }
}
