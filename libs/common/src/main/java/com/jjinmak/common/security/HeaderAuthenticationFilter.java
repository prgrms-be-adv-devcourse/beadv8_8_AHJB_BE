package com.jjinmak.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String memberIdHeader = request.getHeader(AuthHeaders.MEMBER_ID);

        if (memberIdHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Long memberId = Long.parseLong(memberIdHeader.trim());
            List<String> roles = parseRoles(request.getHeader(AuthHeaders.MEMBER_ROLES));
            setAuthentication(memberId, roles);
        } catch (NumberFormatException e) {
            log.warn("잘못된 {} 헤더 값: {}", AuthHeaders.MEMBER_ID, memberIdHeader);
        }

        filterChain.doFilter(request, response);
    }


    private List<String> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();
    }

    private void setAuthentication(Long memberId, List<String> roles){
        AuthPrincipal principal = new AuthPrincipal(memberId, roles);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
