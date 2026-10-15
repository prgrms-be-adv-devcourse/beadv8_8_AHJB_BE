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

    public ProductMember(Long id, String nickname) {
        super(id);
        this.nickname = nickname;
    }

    public void sync(String nickname) {
        this.nickname = nickname;
    }
}
