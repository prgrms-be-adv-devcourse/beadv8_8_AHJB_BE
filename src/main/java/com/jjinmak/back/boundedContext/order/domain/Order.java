package com.jjinmak.back.boundedContext.order.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

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
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
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
