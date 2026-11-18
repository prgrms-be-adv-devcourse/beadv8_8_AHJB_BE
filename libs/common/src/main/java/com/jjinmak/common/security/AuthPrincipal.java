package com.jjinmak.common.security;

import java.util.List;

public record AuthPrincipal(
        Long memberId,
        List<String> authorities
) {}
