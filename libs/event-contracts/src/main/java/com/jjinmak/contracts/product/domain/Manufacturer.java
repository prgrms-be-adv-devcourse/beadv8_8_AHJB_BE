package com.jjinmak.contracts.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 제조사 (상품 서비스와 검색 서비스가 같이 쓴다)
// 이벤트와 검색 문서에 이름(name())으로 저장되므로 이름을 바꾸면 안 된다.
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

    // 실제로 보여줄 제조사 이름. 기타(ETC)면 판매자가 직접 입력한 이름을 쓴다.
    public String resolveDisplayName(String manufacturerEtc) {
        return this == ETC ? manufacturerEtc : displayName;
    }
}
