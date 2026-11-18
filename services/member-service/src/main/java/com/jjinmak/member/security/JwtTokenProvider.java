package com.jjinmak.member.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    private final PrivateKey privateKey;
    private final long accessTokenExpiration;


    public JwtTokenProvider(
            @Value("${jwt.private-key}") String privateKey,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration
    ) {
        this.privateKey = parsePrivateKey(privateKey);
        this.accessTokenExpiration = accessTokenExpiration;
    }

    private static PrivateKey parsePrivateKey(String base64Der) {
        try {
            byte[] der = Decoders.BASE64.decode(base64Der);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JWT 개인키 형식이 올바르지 않습니다.", e);
        }
    }

    public String createAccessToken(Long memberId, List<String> authorities){
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(privateKey, Jwts.SIG.RS256)
                .claim("auth", String.join(",", authorities))
                .compact();
    }

}
