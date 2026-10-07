package com.jjinmak.back.boundedContext.cart.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class CartMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(unique = true)
    private UUID uuid;

    @NotNull
    private String nickname;

    public CartMember(UUID uuid, String nickname){
        this.uuid = uuid;
        this.nickname = nickname;
    }

    public void sync(String nickname){
        this.nickname = nickname;
    }
}
