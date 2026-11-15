package com.jjinmak.recommend.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"member_id", "auction_id", "type"})
})
public class Interaction extends BaseIdAndTime {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    private RecommendMember member;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    private RecommendAuction auction;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    private RecommendProduct product;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private InteractionType type;

    private Boolean active;

    private LocalDateTime lastOccurredAt;

    public Interaction(
            RecommendMember member, RecommendAuction auction, RecommendProduct product,
            InteractionType type, LocalDateTime occurredAt
    ){
        this.member = member;
        this.auction = auction;
        this.product = product;
        this.active = true;
        this.type = type;
        this.lastOccurredAt = occurredAt;
    }

    public boolean isNew(LocalDateTime occurredAt){
        return !occurredAt.isBefore(this.lastOccurredAt);
    }

    public void update(LocalDateTime occurredAt){
        if (this.lastOccurredAt == null || occurredAt.isAfter(this.lastOccurredAt))
            this.lastOccurredAt = occurredAt;
    }

    public void activate(){
        this.active = true;
    }

    public void deactivate(){
        this.active = false;
    }
}
