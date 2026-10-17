package com.jjinmak.back.boundedContext.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginLock {

    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    @Column(nullable = false)
    private boolean locked;

    @Column(nullable = false)
    private int loginFailCount;

    private LocalDateTime lockedUntil;

    public static LoginLock unlocked() {
        LoginLock loginLock = new LoginLock();
        loginLock.reset();
        return loginLock;
    }

    public void increaseFailCount(LocalDateTime now) {
        loginFailCount++;
        if (loginFailCount >= MAX_LOGIN_FAIL_COUNT) {
            locked = true;
            lockedUntil = now.plusMinutes(LOCK_DURATION_MINUTES);
        }
    }

    public void reset() {
        locked = false;
        loginFailCount = 0;
        lockedUntil = null;
    }

    public void unlockIfExpired(LocalDateTime now) {
        if (locked && lockedUntil.isBefore(now)) {
            reset();
        }
    }

    public boolean isLocked() {
        return locked;
    }
}