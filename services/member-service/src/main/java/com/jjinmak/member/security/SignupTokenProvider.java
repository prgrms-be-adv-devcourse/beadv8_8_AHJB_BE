package com.jjinmak.member.security;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.member.app.SignupTicket;
import com.jjinmak.member.config.SignupTokenProperties;
import com.jjinmak.member.domain.OAuthProvider;
import com.jjinmak.member.exception.AuthErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * 소셜 가입 대기 토큰.
 * 카카오 인증은 끝났지만 추가 정보 입력 전인 사용자에게 발급한다.
 * - 서버에 상태를 저장하지 않으므로 member 인스턴스를 여러 개 띄워도 동작한다.
 * - 액세스 토큰(RS256)과 다른 키(HS256)로 서명해, 게이트웨이에서는 검증이 실패하도록 한다.
 * - 회원번호를 요청 본문으로 받지 않고 이 토큰에서만 꺼내므로 위조할 수 없다.
 */
@Component
public class SignupTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String TOKEN_TYPE = "oauth_signup";
    private static final String PROVIDER_CLAIM = "provider";
    private static final String EMAIL_CLAIM = "email";

    private final SecretKey key;
    private final Duration expiration;

    public SignupTokenProvider(SignupTokenProperties properties) {
        // 256비트 미만이면 WeakKeyException 으로 기동이 실패한다(약한 키로 운영되는 것을 막는다)
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.expiration();
    }

    public String create(SignupTicket ticket) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration.toMillis());

        // 서명만 되고 암호화되지는 않으므로 이메일은 클라이언트가 읽을 수 있다(본인 이메일이라 문제없음).
        // 서명 덕분에 클라이언트가 이메일을 바꾸면 검증에 실패한다. 이메일이 없으면 클레임을 넣지 않는다.
        return Jwts.builder()
                .subject(ticket.providerUserId())
                .claim(PROVIDER_CLAIM, ticket.provider().name())
                .claim(EMAIL_CLAIM, ticket.verifiedEmail())
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public SignupTicket parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .require(TOKEN_TYPE_CLAIM, TOKEN_TYPE)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String provider = claims.get(PROVIDER_CLAIM, String.class);
            String providerUserId = claims.getSubject();
            if (provider == null || providerUserId == null || providerUserId.isBlank()) {
                throw new BusinessException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
            }
            String verifiedEmail = claims.get(EMAIL_CLAIM, String.class);
            return new SignupTicket(OAuthProvider.valueOf(provider), providerUserId, verifiedEmail);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthErrorCode.EXPIRED_SIGNUP_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
        }
    }
}
