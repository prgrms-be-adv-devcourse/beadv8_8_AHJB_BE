package com.jjinmak.member.in.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequestDto(
        @NotBlank
        @Schema(description = "카카오가 redirect URI 로 전달한 인가 코드 (state 검증은 프론트에서 완료한 뒤 보낸다)")
        String code
) {
}
