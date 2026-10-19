package com.jjinmak.back.boundedContext.order.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.*;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_FORBIDDEN;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;

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
    Long auctionId;

    @NotNull
    Long productId;

    @NotNull
    Long winningPrice;

    @NotNull
    Long deliveryFee;

    @Column(nullable = true)
    LocalDateTime confirmExpiredAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    OrderState state;

    public Order(OrderGroup group, OrderMember winner, OrderMember seller, Long auctionId, Long productId, Long winningPrice, Long deliveryFee){
        this.group = group;
        this.winner = winner;
        this.seller = seller;
        this.auctionId = auctionId;
        this.productId = productId;
        this.winningPrice = winningPrice;
        this.deliveryFee = deliveryFee;
        this.state = OrderState.WAITING;
    }

    public void updateState(OrderState state){
        this.state = state;
    }

    public void validateAndUpdateState(OrderState before, OrderState after){
        validateState(before);
        this.state = after;
    }

    public void validateWinner(OrderMember member){
        if (!this.winner.getId().equals(member.getId()))
            throw new BusinessException(ORDER_FORBIDDEN);
    }

    public void validateSeller(OrderMember member){
        if (!this.seller.getId().equals(member.getId()))
            throw new BusinessException(ORDER_FORBIDDEN);
    }

    public void validateParticipant(OrderMember member){
        if (!this.winner.getId().equals(member.getId()) && !this.seller.getId().equals(member.getId())){
            throw new BusinessException(ORDER_FORBIDDEN);
        }
    }

    public void validateState(OrderState state){
        if (!this.state.equals(state)){
            throw new BusinessException(ORDER_STATE_BAD_REQUEST);
        }
    }

    public void shipped(LocalDateTime shippedAt){
        if (this.state != SHIPPING && this.state != DELIVERY_ERROR)
            throw new BusinessException(ORDER_STATE_BAD_REQUEST);

        this.state = SHIPPED;
        this.confirmExpiredAt = shippedAt.plusDays(7);
    }
}
