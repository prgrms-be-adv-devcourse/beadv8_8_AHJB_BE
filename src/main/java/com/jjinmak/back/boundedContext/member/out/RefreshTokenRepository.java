package com.jjinmak.back.boundedContext.member.out;

import com.jjinmak.back.boundedContext.member.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByRefreshToken(String RefreshToken);
}
