package com.jjinmak.back.boundedContext.cart.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    private CartMember winner;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    private CartMember seller;

    @NotNull
    private Long productId;

    private Long winningPrice;

    private Long deliveryFee;

    @NotNull
    private LocalDateTime winAt;

    @NotNull
    private LocalDateTime paymentDueAt;

    public CartItem(CartMember winner, CartMember seller, Long productId, Long winningPrice, Long deliveryFee, LocalDateTime winAt){
        this.winner = winner;
        this.seller = seller;
        this.productId = productId;
        this.winningPrice = winningPrice;
        this.deliveryFee = deliveryFee;
        this.winAt = winAt;
        this.paymentDueAt = winAt.plusDays(1);
    }
}
