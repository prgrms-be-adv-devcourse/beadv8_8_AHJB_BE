package com.jjinmak.back.shared.member.dto;

import java.time.LocalDateTime;

public record MemberWarningDto(

        Long memberId,//대상
        String sourceContext, //경고가 발생한 컨텍스트 #todo enum변경 고려
        Long sourceId, // 원인건 식별자
        String reason, // 구체적 사유 "CANCEL" | "ABUSE" | ... #todo enum변경 고려
        String description, // 자유 텍스트 Nullable
        LocalDateTime occurredAt // 발생 시각
){}
