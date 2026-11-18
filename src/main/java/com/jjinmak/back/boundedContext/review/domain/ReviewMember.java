package com.jjinmak.back.boundedContext.review.domain;

import com.jjinmak.back.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewMember extends BaseManualIdAndTime {

    @Column(nullable = false)
    private String nickname;

    public ReviewMember(Long id, String nickname) {
        super(id);
        this.nickname = nickname;
    }

    public void sync(String nickname) {
        this.nickname = nickname;
    }
}
