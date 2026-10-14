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

import javax.naming.AuthenticationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseIdAndTime {

    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    @Column(nullable = false, unique = true)
    private UUID uuid;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String phone;

    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false)
    private boolean isSeller;

    @Column(nullable = false)
    private int loginFailCount;

    private LocalDateTime lockedUntil;

    private Member(String email, String encodedPassword, String nickname, String username,
                   String phone, LocalDate birthDate){
        this.uuid = UUID.randomUUID();
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.username = username;
        this.phone = phone;
        this.birthDate = birthDate;
        this.status = AccountStatus.ACTIVE;
        this.isSeller = false;
        this.loginFailCount = 0;
    }

    public static Member create(String email, String encodedPassword, String nickname, String username, String phone,
                                LocalDate birthDate){
        return new Member(email, encodedPassword, nickname, username, phone, birthDate);
    }

    public void increaseLoginFailCount(){
        loginFailCount++;
        if(loginFailCount>=MAX_LOGIN_FAIL_COUNT){
            status = AccountStatus.LOCKED;
            lockedUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
        }
    }

    public void resetLoginFailCount(){
        loginFailCount = 0;
        lockedUntil = null;
        status = AccountStatus.ACTIVE;
    }

    public boolean validPassword(PasswordEncoder passwordEncoder, String password){
        return passwordEncoder.matches(password, this.password);
    }

    public void unlockIfExpired() {
        if (status == AccountStatus.LOCKED && lockedUntil.isBefore(LocalDateTime.now())) {
            status = AccountStatus.ACTIVE;
            loginFailCount = 0;
            lockedUntil = null;
        }
    }

    public boolean isBanned(){
        return status == AccountStatus.BANNED;
    }

    public boolean isLocked(){
        return status == AccountStatus.LOCKED;
    }

    public boolean isSuspended(){return status == AccountStatus.SUSPENDED; }

    public void registerAsSeller(){
        if(isSeller){
            throw new BusinessException(MemberErrorCode.ALREADY_REGISTERED_SELLER);
        }

        switch (status) {
            case SUSPENDED -> throw new BusinessException(CommonErrorCode.SUSPENDED_ACCOUNT);
            case BANNED -> throw new BusinessException(CommonErrorCode.BANNED_ACCOUNT);
            default -> { }
        }

        this.isSeller = true;
    }

    public List<String> getAuthorities(){
        List<String> authorities = new ArrayList<>();

        authorities.add(Authority.ROLE_BUYER);

        if(isSeller()){
            authorities.add(Authority.ROLE_SELLER);
        }
        if(!isSuspended()){
            authorities.add(Authority.PERM_TRADE);
            if(isSeller()){
                authorities.add(Authority.PERM_SELL);
            }
        }
        return authorities;
    }
}
