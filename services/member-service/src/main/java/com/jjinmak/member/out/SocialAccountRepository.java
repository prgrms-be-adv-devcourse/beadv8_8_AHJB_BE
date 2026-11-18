package com.jjinmak.member.out;

import com.jjinmak.member.domain.OAuthProvider;
import com.jjinmak.member.domain.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    boolean existsByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
