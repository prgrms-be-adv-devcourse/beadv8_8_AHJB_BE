package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShippingFeeType {
    FREE(0L),
    FIXED_3000(3000L),
    FIXED_4000(4000L),
    CUSTOM(null);

    private final Long fee;

    public Long resolveFee(Long customFee) {
        if (this != CUSTOM) {
            return fee;
        }
        if (customFee == null) {
            throw new BusinessException(ProductErrorCode.DIRECT_INPUT_REQUIRED);
        }
        return customFee;
    }
}
