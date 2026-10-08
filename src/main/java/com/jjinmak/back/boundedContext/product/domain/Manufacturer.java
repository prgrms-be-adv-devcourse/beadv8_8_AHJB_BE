package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.exception.BusinessException;

public enum Manufacturer {
    NINTENDO,
    SONY,
    MICROSOFT,
    SEGA,
    ETC;

    public String resolveEtcName(String manufacturerEtc) {
        if (this != ETC) {
            return null;
        }
        if (manufacturerEtc == null || manufacturerEtc.isBlank()) {
            throw new BusinessException(ProductErrorCode.DIRECT_INPUT_REQUIRED);
        }
        return manufacturerEtc.strip();
    }
}
