package com.jjinmak.order.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import com.jjinmak.contracts.cart.dto.CartItemDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class CartItem extends BaseIdAndTime {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    private OrderMember winner;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    private OrderMember seller;

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

    @NotNull
    @ColumnDefault("false")
    private Boolean paymentDefaultAlarmed = false;

    public CartItem(OrderMember winner, OrderMember seller, Long auctionId, Long productId, Long winningPrice, Long deliveryFee, LocalDateTime winAt){
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

    public void sendPaymentReminder(){
        paymentDefaultAlarmed = true;
    }

}
