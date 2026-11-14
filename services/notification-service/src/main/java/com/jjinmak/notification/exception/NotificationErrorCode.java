package com.jjinmak.notification.exception;

import com.jjinmak.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
    NOTIFICATION_NOT_FOUND("NOTIFICATION001", "존재하지 않는 알림입니다.", HttpStatus.NOT_FOUND),
    PUSH_SETTING_NOT_ALLOWED("NOTIFICATION002", "푸시를 끌 수 없는 알림 묶음입니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}