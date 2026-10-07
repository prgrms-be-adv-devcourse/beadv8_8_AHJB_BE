package com.jjinmak.back.boundedContext.auction.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
public class AuctionMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;
    @Column(nullable = false,unique = true)
    private UUID uuid;
    @Column(nullable = false)
    private String nickname;
    //private status 패널티 회원 판별으로 추가예정

    public AuctionMember(UUID uuid,String nickname){
        this.uuid = uuid;
        this.nickname = nickname;
    }
    public void sync(String nickname){
        this.nickname =nickname;
    }
}
