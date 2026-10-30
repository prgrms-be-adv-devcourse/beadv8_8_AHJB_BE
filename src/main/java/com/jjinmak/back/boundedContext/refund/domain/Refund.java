package com.jjinmak.back.boundedContext.refund.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.shared.refund.domain.RefundStatus;
import com.jjinmak.back.shared.refund.dto.RefundDetailDto;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseIdAndTime {

    @NotNull
    @Column(unique = true)
    private Long orderId;

    @NotNull
    private Long winnerId;

    @NotNull
    private Long sellerId;

    @NotNull
    private Long productId;

    @NotNull
    private Long amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private RefundStatus status;

    private LocalDateTime processedAt;

    @Version
    private Long version;

    public Refund(Long orderId, Long winnerId, Long sellerId, Long productId, Long amount) {
        this.orderId = orderId;
        this.winnerId = winnerId;
        this.sellerId = sellerId;
        this.productId = productId;
        this.amount = amount;
        this.status = RefundStatus.REQUESTED;
    }

    public RefundDto dto() {
        return new RefundDto(
                getId(),
                orderId,
                winnerId,
                status,
                amount,
                getCreatedAt(),
                processedAt);
    }

    public boolean isParticipant(Long memberId) {
        return winnerId.equals(memberId) || sellerId.equals(memberId);
    }

    public boolean isSeller(Long memberId) {
        return sellerId.equals(memberId);
    }

    public boolean isAccepted() {
        return status == RefundStatus.ACCEPTED;
    }

    public void accept() {
        process(RefundStatus.ACCEPTED);
    }

    public void reject() {
        process(RefundStatus.REJECTED);
    }

    public void complete() {
        if (status != RefundStatus.ACCEPTED) {
            throw new BusinessException(RefundErrorCode.NOT_ACCEPTED);
        }
        this.status = RefundStatus.COMPLETED;
    }

    private void process(RefundStatus result) {
        if (status != RefundStatus.REQUESTED) {
            throw new BusinessException(RefundErrorCode.ALREADY_PROCESSED);
        }
        this.status = result;
        this.processedAt = LocalDateTime.now();
    }

    public RefundDetailDto detailDto() {
        return new RefundDetailDto(
                getId(),
                orderId,
                productId,
                amount,
                status,
                getCreatedAt(),
                processedAt
        );
    }
}
