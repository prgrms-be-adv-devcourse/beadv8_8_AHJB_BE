package com.jjinmak.boundedcontext.market.shared.domain;

import com.jjinmak.common.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// market(상품·경매·후기)이 함께 쓰는 회원. 회원 가입 이벤트로 복제한다. (id는 회원 서비스의 id 그대로)
// 판매자, 입찰자, 찜·문의·후기 작성자가 모두 이 회원을 가리킨다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketMember extends BaseManualIdAndTime {

    @Column(nullable = false)
    private String nickname;

    public MarketMember(Long id, String nickname) {
        super(id);
        this.nickname = nickname;
    }

    public void sync(String nickname) {
        this.nickname = nickname;
    }
}
