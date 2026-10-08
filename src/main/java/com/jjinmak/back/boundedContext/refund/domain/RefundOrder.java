package com.jjinmak.back.boundedContext.refund.domain;

import com.jjinmak.back.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundOrder extends BaseManualIdAndTime {

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
        super(orderId);
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


























