package com.jjinmak.back.boundedContext.order.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class OrderMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @NotNull
    @Column(unique = true)
    UUID uuid;

    @NotNull
    String nickname;

    public OrderMember(UUID uuid, String nickname){
        this.uuid = uuid;
        this.nickname = nickname;
    }

    public void sync(String nickname){
        this.nickname = nickname;
    }
}
