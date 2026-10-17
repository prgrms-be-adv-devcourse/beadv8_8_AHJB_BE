package com.jjinmak.back.boundedContext.order.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class CartItem extends BaseIdAndTime {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    private CartMember winner;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    private CartMember seller;

    @NotNull
    @Column(unique = true)
    private Long auctionId;

    @NotNull
    private Long productId;

    private Long winningPrice;

    private Long deliveryFee;

    @NotNull
    private LocalDateTime winAt;

    @NotNull
    private LocalDateTime paymentDueAt;

    public CartItem(CartMember winner, CartMember seller, Long auctionId, Long productId, Long winningPrice, Long deliveryFee, LocalDateTime winAt){
        this.winner = winner;
        this.seller = seller;
        this.auctionId = auctionId;
        this.productId = productId;
        this.winningPrice = winningPrice;
        this.deliveryFee = deliveryFee;
        this.winAt = winAt;
        this.paymentDueAt = winAt.plusDays(1);
    }

    public CartItemDto dto(){
        return new CartItemDto(
                this.getId(), winner.getUuid(), seller.getUuid(),
                auctionId, productId, winningPrice, deliveryFee,
                winAt, paymentDueAt
        );
    }
}
