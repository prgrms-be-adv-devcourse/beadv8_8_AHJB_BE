package com.jjinmak.back.boundedContext.cart.domain;

import com.jjinmak.back.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class CartMember extends BaseManualIdAndTime {

    @NotNull
    @Column(unique = true)
    private UUID uuid;

    @NotNull
    private String nickname;

    public CartMember(Long id, UUID uuid, String nickname){
        super(id);
        this.uuid = uuid;
        this.nickname = nickname;
    }

    public void sync(String nickname){
        this.nickname = nickname;
    }
}
