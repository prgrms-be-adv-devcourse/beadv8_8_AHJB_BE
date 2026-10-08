package com.jjinmak.back.boundedContext.refund.domain;

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
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @NotNull
    private LocalDateTime requestedAt;

    public Refund(RefundOrder refundOrder, RefundReason reason,
                  String detail, LocalDateTime requestedAt) {
        this.refundOrder = refundOrder;
        this.reason = reason;
        this.detail = detail;
        this.amount = refundOrder.PaymentAmount();
        this.status = RefundStatus.REQUESTED;
        this.requestedAt = requestedAt;
    }

    public RefundDto dto() {
        return new RefundDto(id, refundOrder.getOrderId(), status, amount, requestedAt);
    }
}
