package com.jjinmak.back.boundedContext.member.in.dto;

import java.time.LocalDate;

public record MemberDetailResponseDto(
        String nickname,
        String email,
        String username,
        String phone,
        LocalDate birthDate,
        String addressAlias,
        String address,
        LocalDate signUpDate
){}
