package com.jjinmak.back.boundedContext.order.domain;

import com.jjinmak.back.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class OrderMember extends BaseManualIdAndTime {

    @NotNull
    @Column(unique = true)
    UUID uuid;

    @NotNull
    String nickname;

    public OrderMember(Long id, UUID uuid, String nickname){
        super(id);
        this.uuid = uuid;
        this.nickname = nickname;
    }

    public void sync(String nickname){
        this.nickname = nickname;
    }
}
