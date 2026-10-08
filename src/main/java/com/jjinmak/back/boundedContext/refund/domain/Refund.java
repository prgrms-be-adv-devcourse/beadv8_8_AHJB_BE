package com.jjinmak.back.boundedContext.refund.domain;

import com.jjinmak.back.global.jpa.entity.BaseEntity;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private RefundOrder refundOrder;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private RefundReason reason;

    @Column(length = 500)
    private String detail;

    @NotNull
    private Long amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private RefundStatus status;

    public Refund(RefundOrder refundOrder, RefundReason reason, String detail) {
        this.refundOrder = refundOrder;
        this.reason = reason;
        this.detail = detail;
        this.amount = refundOrder.PaymentAmount();
        this.status = RefundStatus.REQUESTED;
    }

    public RefundDto dto() {
        return new RefundDto(getId(), refundOrder.getId(), status, amount, getCreatedAt());
    }
}
