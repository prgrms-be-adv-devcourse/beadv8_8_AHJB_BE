package com.jjinmak.member.security;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.member.app.SignupTicket;
import com.jjinmak.member.config.SignupTokenProperties;
import com.jjinmak.member.domain.OAuthProvider;
import com.jjinmak.member.exception.AuthErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignupTokenProviderTest {

    private static final String SECRET = "test-oauth-signup-token-secret-0123456789abcdef";

    private final SignupTokenProvider provider =
            new SignupTokenProvider(new SignupTokenProperties(SECRET, Duration.ofMinutes(10)));

    @Test
    void 발급한_토큰에서_외부_계정_정보를_꺼낸다() {
        String token = provider.create(new SignupTicket(OAuthProvider.KAKAO, "12345"));

        SignupTicket ticket = provider.parse(token);

        assertThat(ticket.provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(ticket.providerUserId()).isEqualTo("12345");
    }

    @Test
    void 만료된_토큰은_거부한다() {
        SignupTokenProvider expiredProvider =
                new SignupTokenProvider(new SignupTokenProperties(SECRET, Duration.ofSeconds(-1)));
        String token = expiredProvider.create(new SignupTicket(OAuthProvider.KAKAO, "12345"));

        assertThatThrownBy(() -> provider.parse(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.EXPIRED_SIGNUP_TOKEN);
    }

    @Test
    void 다른_키로_서명된_토큰은_거부한다() {
        SignupTokenProvider other = new SignupTokenProvider(
                new SignupTokenProperties("another-secret-value-for-signup-token-0123456789", Duration.ofMinutes(10)));
        String token = other.create(new SignupTicket(OAuthProvider.KAKAO, "12345"));

        assertThatThrownBy(() -> provider.parse(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN);
    }

    @Test
    void 같은_키라도_가입_대기_토큰_타입이_아니면_거부한다() {
        String token = Jwts.builder()
                .subject("12345")
                .claim("provider", "KAKAO")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> provider.parse(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN);
    }

    @Test
    void 형식이_잘못된_토큰은_거부한다() {
        assertThatThrownBy(() -> provider.parse("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN);
    }

    @Test
    void 키가_256비트보다_짧으면_생성_시점에_실패한다() {
        assertThatThrownBy(() -> new SignupTokenProvider(new SignupTokenProperties("short", Duration.ofMinutes(10))))
                .isInstanceOf(WeakKeyException.class);
    }
}
