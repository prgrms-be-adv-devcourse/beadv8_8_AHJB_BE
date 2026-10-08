package com.jjinmak.back.boundedContext.refund.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(unique = true)
    private Long orderId;

    @NotNull
    private Long winnerId;

    @NotNull
    private Long sellerId;

    @NotNull
    private Long winningPrice;

    @NotNull
    private Long deliveryFee;

    private LocalDateTime confirmedAt;

    public RefundOrder(Long orderId, Long winnerId, Long sellerId, Long winningPrice, Long deliveryFee) {
        this.orderId = orderId;
        this.winnerId = winnerId;
        this.sellerId = sellerId;
        this.winningPrice = winningPrice;
        this.deliveryFee = deliveryFee;
    }

    public void sync(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public boolean isWinner(Long memberId) {
        return winnerId.equals(memberId);
    }

    public boolean isRefundable() {
        return confirmedAt == null;
    }

    public Long PaymentAmount() {
        return winningPrice + deliveryFee;
    }

}


























