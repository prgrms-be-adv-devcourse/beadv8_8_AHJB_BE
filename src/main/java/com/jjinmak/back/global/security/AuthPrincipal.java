package com.jjinmak.back.global.security;

import java.util.List;

public record AuthPrincipal(
        Long memberId,
        List<String> authorities
) {}
