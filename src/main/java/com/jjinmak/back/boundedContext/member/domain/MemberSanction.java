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
@Table(name = "member_sanction", indexes = @Index(
        name="idx_member_sanction_member_id",
        columnList = "member_id"
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSanction extends BaseIdAndTime {

    private static final int FIRST_SUSPENSION_DAYS = 7;
    private static final int SECOND_SUSPENSION_DAYS = 30;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SanctionType sanctionType;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(nullable = false)
    private int warningCount;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime expiredAt;

    private LocalDateTime releasedAt;

    private MemberSanction(Long memberId, SanctionType type, String reason, int warningCount,
                           LocalDateTime startedAt, LocalDateTime expiredAt, LocalDateTime releasedAt){
        this.memberId = memberId;
        this.sanctionType = type;
        this.reason = reason;
        this.warningCount = warningCount;
        this.startedAt = startedAt;
        this.expiredAt = expiredAt;
        this.releasedAt = releasedAt;
    }

    public static MemberSanction sanctionFrom(MemberWarningDto warning, int warningCount, LocalDateTime now){
        if (warningCount <= 1) {
            return new MemberSanction(warning.memberId(), SanctionType.SUSPENDED, warning.reason(), warningCount,
                    now, now.plusDays(FIRST_SUSPENSION_DAYS), null);
        }
        if (warningCount == 2) {
            return new MemberSanction(warning.memberId(), SanctionType.SUSPENDED, warning.reason(), warningCount,
                    now, now.plusDays(SECOND_SUSPENSION_DAYS), null);
        }
        return new MemberSanction(warning.memberId(), SanctionType.BAN, warning.reason(), warningCount,
                now, null, null);
    }

    public boolean isExpired(LocalDateTime now) {
        return expiredAt != null && expiredAt.isBefore(now);
    }

    public void release(LocalDateTime now){
        this.releasedAt = now;
    }
}