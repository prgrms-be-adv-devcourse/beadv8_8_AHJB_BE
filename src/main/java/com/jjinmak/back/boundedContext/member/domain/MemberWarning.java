package com.jjinmak.back.boundedContext.member.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.shared.member.dto.MemberWarningDto;
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

    @Column(nullable = false)
    private String sourceContext;

    @Column(nullable = false)
    private String reason;

    private String description;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private MemberWarning(Long memberId, String sourceContext, String reason, String description, Long sourceId, LocalDateTime occurredAt){
        this.memberId = memberId;
        this.sourceContext = sourceContext;
        this.reason = reason;
        this.description = description;
        this.sourceId = sourceId;
        this.occurredAt = occurredAt;
    }

    public static MemberWarning from(MemberWarningDto memberWarningDto){
        return new MemberWarning(memberWarningDto.memberId(), memberWarningDto.sourceContext(), memberWarningDto.reason() ,memberWarningDto.description(), memberWarningDto.sourceId(), memberWarningDto.occurredAt());
    }
}
