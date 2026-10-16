package com.jjinmak.back.boundedContext.member.out;

import com.jjinmak.back.boundedContext.member.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    List<RefreshToken> findAllByMemberIdAndRevokedFalse(Long memberId);
}