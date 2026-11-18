package com.jjinmak.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

@Component
public class JwtTokenVerifier {

    private final PublicKey publicKey;

    public JwtTokenVerifier(@Value("${jwt.public-key}") String publicKey) {
        this.publicKey = parsePublicKey(publicKey);
    }

    public TokenVerification verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return TokenVerification.valid(
                    Long.valueOf(claims.getSubject()),
                    claims.get("auth", String.class));
        } catch (ExpiredJwtException e) {
            return TokenVerification.expired();
        } catch (JwtException | IllegalArgumentException e) {
            return TokenVerification.invalid();
        }
    }

    private static PublicKey parsePublicKey(String base64Der) {
        try {
            byte[] der = Decoders.BASE64.decode(base64Der);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JWT 공개키 형식이 올바르지 않습니다.", e);
        }
    }
}