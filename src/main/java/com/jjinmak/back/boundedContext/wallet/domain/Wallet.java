package com.jjinmak.back.boundedContext.wallet.domain;

import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.*;

@Entity
@NoArgsConstructor
@Getter
public class Wallet extends BaseIdAndTime {

    @Column(nullable = false)
    private long balance;

    @Version
    @Column(nullable = false)
    private long version;

    // USER 타입만 소유자가 있고, ESCROW/FEE 시스템 지갑은 null
    @ManyToOne(fetch = FetchType.LAZY)
    private WalletMember holder;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length=20)
    private WalletType type;

//    @Enumerated(EnumType.STRING)
//    @JdbcTypeCode(SqlTypes.VARCHAR)
//    @Column(nullable = false, length = 20)
//    private WalletStatus status;


    public Wallet(WalletMember holder, WalletType type) {
        this.holder = holder;
        this.type = type;
        this.balance = 0L;
    }

    public static Wallet createUserWallet(WalletMember member) {
        return new Wallet(member, WalletType.USER);
    }

    public static Wallet createSystemWallet(WalletType type) {
        if (type == WalletType.USER) {
            throw new BusinessException(INVALID_SYSTEM_WALLET_TYPE);
        }
        return new Wallet(null, type);
    }

    public void increase(long amount) {
        validatePositive(amount);
        this.balance += amount;
    }

    public void decrease(long amount) {
        validatePositive(amount);

        if (this.balance < amount) {
            throw new BusinessException(INSUFFICIENT_BALANCE);
        }
        this.balance -= amount;
    }

    private void validatePositive(long amount) {
        if (amount <= 0) {
            throw new BusinessException(INVALID_AMOUNT);
        }
    }

    public boolean hasEnough(long amount) {
        return this.balance >= amount;
    }
}
