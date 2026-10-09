package com.jjinmak.back.boundedContext.wallet.domain;

import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import com.jjinmak.back.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@NoArgsConstructor
@Getter
public class Wallet extends BaseManualIdAndTime {

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
        super(holder.getId());
        this.holder = holder;
        this.type = type;
        this.balance = 0L;
    }

    public static Wallet createUserWallet(WalletMember member) {
        return new Wallet(member, WalletType.USER);
    }

    public static Wallet createSystemWallet(WalletType type) {
        if (type == WalletType.USER) {
            throw new IllegalArgumentException("USER 타입은 시스템 지갑이 될 수 없습니다.");
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
            throw new IllegalStateException("잔액이 부족합니다. balance=" + balance + ", requested=" + amount);
        }
        this.balance -= amount;
    }

    private void validatePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다. amount=" + amount);
        }
    }

    public boolean hasEnough(long amount) {
        return this.balance >= amount;
    }
}
