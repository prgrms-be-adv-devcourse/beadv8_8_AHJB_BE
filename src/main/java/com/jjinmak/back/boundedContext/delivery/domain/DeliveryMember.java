package com.jjinmak.back.boundedContext.delivery.domain;

import com.jjinmak.back.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@NoArgsConstructor
public class DeliveryMember extends BaseManualIdAndTime {

    @NotNull
    @Column(unique = true)
    UUID uuid;

    @NotNull
    String nickname;

    public DeliveryMember(Long id, UUID uuid, String nickname){
        super(id);
        this.uuid = uuid;
        this.nickname = nickname;
    }

    public void sync(String nickname){
        this.nickname = nickname;
    }
}
