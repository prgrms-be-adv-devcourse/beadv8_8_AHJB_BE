package com.jjinmak.gateway.auth;

public record TokenVerification(Status status, Long memberId, String roles) {

    public enum Status { VALID, EXPIRED, INVALID }

    public static TokenVerification valid(Long memberId, String roles) {
        return new TokenVerification(Status.VALID, memberId, roles == null ? "" : roles);
    }
    public static TokenVerification expired() { return new TokenVerification(Status.EXPIRED, null, null); }
    public static TokenVerification invalid() { return new TokenVerification(Status.INVALID, null, null); }
}