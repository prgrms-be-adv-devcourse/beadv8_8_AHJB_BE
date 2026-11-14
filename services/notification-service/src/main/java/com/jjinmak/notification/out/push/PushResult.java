package com.jjinmak.notification.out.push;

public record PushResult(String token, Status status) {

    public enum Status {
        SUCCESS,
        INVALID_TOKEN,
        FAILED
    }

    public static PushResult success(String token) {
        return new PushResult(token, Status.SUCCESS);
    }

    public static PushResult invalidToken(String token) {
        return new PushResult(token, Status.INVALID_TOKEN);
    }

    public static PushResult failed(String token) {
        return new PushResult(token, Status.FAILED);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isInvalidToken() {
        return status == Status.INVALID_TOKEN;
    }
}