package com.jjinmak.back.global.security;

import com.jjinmak.back.global.exception.AuthErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    public static final String EXCEPTION_ATTRIBUTE = "authErrorCode";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain
    ) throws ServletException, IOException {

        String token = resolveToken(request);

        if(token == null){
            filterChain.doFilter(request, response);
            return;
        }

        TokenValidationResult validationResult = jwtTokenProvider.validateToken(token);

        switch (validationResult) {
            case VALID -> setAuthentication(token);
            case EXPIRED -> request.setAttribute(EXCEPTION_ATTRIBUTE, AuthErrorCode.EXPIRED_TOKEN);
            case INVALID -> request.setAttribute(EXCEPTION_ATTRIBUTE, AuthErrorCode.INVALID_TOKEN);
        }


        filterChain.doFilter(request, response);
    }

    private void setAuthentication(String token){

        Long memberId = jwtTokenProvider.getMemberId(token);
        List<String> authorities = jwtTokenProvider.getAuthorities(token);

        AuthPrincipal principal = new AuthPrincipal(memberId, authorities);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

    }

    private String resolveToken(HttpServletRequest request){
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if(header == null){
            return null;
        }
        if(!header.startsWith(BEARER_PREFIX)){
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }
}
