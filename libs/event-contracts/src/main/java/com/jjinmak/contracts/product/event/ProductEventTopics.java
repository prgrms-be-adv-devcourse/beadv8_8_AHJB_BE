package com.jjinmak.contracts.product.event;

public final class ProductEventTopics {

    public static final String PRODUCT_REGISTERED = "product.registered";

    // 상품 등록·수정·삭제 (검색 인덱스 동기화용)
    public static final String PRODUCT_CHANGED = "product.changed";

    // 찜 등록·취소 (추천 관심도, 찜한 상품 알림용)
    public static final String PRODUCT_WISH_CHANGED = "product.wish.changed";

    private ProductEventTopics() {
    }
}
