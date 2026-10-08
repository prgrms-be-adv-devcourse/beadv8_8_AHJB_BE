package com.jjinmak.back.boundedContext.order.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "orders")
public class Order extends BaseIdAndTime {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    OrderGroup group;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    OrderMember winner;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    OrderMember seller;

    @NotNull
    Long productId;

    @NotNull
    Long winningPrice;

    @NotNull
    Long deliveryFee;

    @NotNull
    OrderState state;

    public Order(OrderGroup group, OrderMember winner, OrderMember seller, Long productId, Long winningPrice, Long deliveryFee){
        this.group = group;
        this.winner = winner;
        this.seller = seller;
        this.productId = productId;
        this.winningPrice = winningPrice;
        this.deliveryFee = deliveryFee;
        this.state = OrderState.WAITING;
    }
}
