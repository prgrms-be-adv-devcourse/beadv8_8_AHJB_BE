package com.jjinmak.common.exception;

public record ErrorResponse(
        boolean isSuccess,
        String errorCode,
        String message
) {
}
