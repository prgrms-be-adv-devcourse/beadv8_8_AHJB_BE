package com.jjinmak.contracts.product.domain;

// 상품 카테고리 (상품 서비스와 검색 서비스가 같이 쓴다)
// 이벤트와 검색 문서에 이름(name())으로 저장되므로 이름을 바꾸면 안 된다.
public enum ProductCategory {
    CONSOLE,          // 게임기 본체
    GAME_SOFTWARE,    // 게임 타이틀
    ACCESSORY,        // 주변기기
    LIMITED_EDITION,  // 한정판
    ETC               // 기타
}
