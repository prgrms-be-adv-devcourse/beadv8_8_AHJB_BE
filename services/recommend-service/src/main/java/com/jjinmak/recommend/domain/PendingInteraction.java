package com.jjinmak.recommend.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import com.jjinmak.contracts.product.event.ProductWishChangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class PendingInteraction extends BaseIdAndTime {

    @Column(nullable = false)
    Long memberId;

    @Column(nullable = false)
    Long productId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    ProductWishChangeType changeType;

    @Column(nullable = false)
    LocalDateTime occurredAt;

    public PendingInteraction(Long memberId, Long productId, ProductWishChangeType changeType, LocalDateTime occurredAt){
        this.memberId = memberId;
        this.productId = productId;
        this.changeType = changeType;
        this.occurredAt = occurredAt;
    }
}
