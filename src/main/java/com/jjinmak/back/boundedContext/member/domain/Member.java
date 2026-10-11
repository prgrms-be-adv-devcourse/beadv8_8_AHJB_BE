package com.jjinmak.back.boundedContext.member.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseIdAndTime {

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
        this.loginFailCount = 0;
    }

    public static Member create(String email, String encodedPassword, String nickname, String username, String phone,
                                LocalDate birthDate){
        return new Member(email, encodedPassword, nickname, username, phone, birthDate);

    }
}
