package com.jjinmak.gateway.auth;

public enum AuthError {
    INVALID_ACCESS_TOKEN("AUTH106", "유효하지 않은 액세스 토큰입니다."),
    EXPIRED_ACCESS_TOKEN("AUTH107", "만료된 액세스 토큰입니다.");

    private final String code;
    private final String message;

    AuthError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() { return code; }
    public String message() { return message; }
}