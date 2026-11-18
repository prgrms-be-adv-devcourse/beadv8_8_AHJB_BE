package com.jjinmak.back.boundedContext.member.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "seller")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller extends BaseIdAndTime {

    @Column(nullable = false, unique = true)
    private Long memberId;

    @Column(nullable = false)
    private String bank;

    //  계좌 실명조회는 외부 서비스 연동이 필요하여 본 프로젝트에서는 형식 검증만 수행합니다.
    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String accountHolder;

    private Seller(Long memberId, String bank, String accountNumber, String accountHolder){
        this.memberId = memberId;
        this.bank = bank;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }

    public static Seller create(Long memberId, String bank, String accountNumber, String accountHolder){
        return new Seller(memberId, bank, accountNumber, accountHolder);
    }
}
