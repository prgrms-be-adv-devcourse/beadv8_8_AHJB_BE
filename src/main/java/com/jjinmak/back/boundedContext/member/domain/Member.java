package com.jjinmak.back.boundedContext.member.domain;

import com.jjinmak.back.boundedContext.member.exception.AuthErrorCode;
import com.jjinmak.back.boundedContext.member.exception.MemberErrorCode;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseIdAndTime {

    @Column(nullable = false, unique = true)
    private UUID uuid;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 20)
    private String phone;

    private LocalDate birthDate;

    @Embedded
    private LoginLock loginLock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false)
    private boolean isSeller;

    private Member(String email, String encodedPassword, String nickname, String username,
                   String phone, LocalDate birthDate){
        this.uuid = UUID.randomUUID();
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.username = username;
        this.phone = phone;
        this.birthDate = birthDate;
        this.loginLock = LoginLock.unlocked();
        this.status = AccountStatus.ACTIVE;
        this.isSeller = false;
    }

    public static Member create(String email, String encodedPassword, String nickname, String username, String phone,
                                LocalDate birthDate){
        return new Member(email, encodedPassword, nickname, username, phone, birthDate);
    }

    public void prepareLogin(LocalDateTime now) {
        loginLock.unlockIfExpired(now);
        validateNotBanned();

        if (loginLock.isLocked()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
    }

    public void validateNotBanned() {
        if (isBanned()) {
            throw new BusinessException(CommonErrorCode.BANNED_ACCOUNT);
        }
    }

    public void increaseLoginFailCount(LocalDateTime now){
        loginLock.increaseFailCount(now);
    }

    public void resetLoginFailCount(){
        loginLock.reset();
    }

    public boolean matchesPassword(PasswordEncoder passwordEncoder, String password){
        return passwordEncoder.matches(password, this.password);
    }

    public boolean isLocked(){
        return loginLock.isLocked();
    }

    public boolean isBanned(){
        return status == AccountStatus.BANNED;
    }

    public boolean isSuspended(){
        return status == AccountStatus.SUSPENDED;
    }

    public void activate(){
        status = AccountStatus.ACTIVE;
    }

    public void suspend(){
        status = AccountStatus.SUSPENDED;
    }

    public void ban(){
        status = AccountStatus.BANNED;
    }

    public void registerAsSeller(){
        if(isSeller){
            throw new BusinessException(MemberErrorCode.ALREADY_REGISTERED_SELLER);
        }
        if(isSuspended()){
            throw new BusinessException(CommonErrorCode.SUSPENDED_ACCOUNT);
        }
        validateNotBanned();

        this.isSeller = true;
    }

    public List<String> getAuthorities() {
        List<String> authorities = new ArrayList<>();
        authorities.add(Authority.ROLE_BUYER);

        if (isSeller()) {
            authorities.add(Authority.ROLE_SELLER);
        }
        if (!isSuspended()) {
            authorities.add(Authority.PERM_TRADE);
        }
        if (isSeller() && !isSuspended()) {
            authorities.add(Authority.PERM_SELL);
        }

        return authorities;
    }
}