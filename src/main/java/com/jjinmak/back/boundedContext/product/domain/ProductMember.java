package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMember extends BaseManualIdAndTime {

    @Column(nullable = false)
    private String nickname;

    // TODO: 판매자 등록 여부, 계정 상태 (회원 컨텍스트 구현 후 동기화)

    public ProductMember(Long id, String nickname) {
        super(id);
        this.nickname = nickname;
    }

    public void sync(String nickname) {
        this.nickname = nickname;
    }
}
