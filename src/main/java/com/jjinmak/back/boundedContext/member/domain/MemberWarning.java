package com.jjinmak.back.boundedContext.member.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "member_warning",
        indexes = @Index(name = "idx_member_warning_member_id", columnList = "member_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_warning_source",
                columnNames = {"source_context", "source_id"}
        ))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberWarning extends BaseIdAndTime {

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 30)
    private String sourceContext;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private MemberWarning(Long memberId, String sourceContext, Long sourceId,
                          String reason, String description, LocalDateTime occurredAt){
        this.memberId = memberId;
        this.sourceContext = sourceContext;
        this.sourceId = sourceId;
        this.reason = reason;
        this.description = description;
        this.occurredAt = occurredAt;
    }

    public static MemberWarning create(Long memberId, String sourceContext, Long sourceId,
                                       String reason, String description, LocalDateTime occurredAt){
        return new MemberWarning(memberId, sourceContext, sourceId, reason, description, occurredAt);
    }
}