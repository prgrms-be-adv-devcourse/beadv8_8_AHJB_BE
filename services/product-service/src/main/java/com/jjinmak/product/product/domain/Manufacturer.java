package com.jjinmak.product.product.domain;

import com.jjinmak.common.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Manufacturer {
    NINTENDO("닌텐도"),
    SONY("소니"),
    MICROSOFT("마이크로소프트"),
    SEGA("세가"),
    ETC("기타");

    // 화면 표시·검색용 한글 이름
    private final String displayName;

    public String resolveEtcName(String manufacturerEtc) {
        if (this != ETC) {
            return null;
        }
        if (manufacturerEtc == null || manufacturerEtc.isBlank()) {
            throw new BusinessException(ProductErrorCode.DIRECT_INPUT_REQUIRED);
        }
        return manufacturerEtc.strip();
    }

    // 실제로 보여줄 제조사 이름. 기타(ETC)면 판매자가 직접 입력한 이름을 쓴다.
    public String resolveDisplayName(String manufacturerEtc) {
        return this == ETC ? manufacturerEtc : displayName;
    }
}
