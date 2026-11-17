package com.jjinmak.auction.domain;

import com.jjinmak.common.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
public class AuctionMember extends BaseManualIdAndTime {

    @Column(nullable = false)
    private String nickname;
    //private status 패널티 회원 판별으로 추가예정

    public AuctionMember(Long id, String nickname) {
        super(id);
        this.nickname = nickname;
    }

    public void sync(String nickname){
        this.nickname =nickname;
    }
}
