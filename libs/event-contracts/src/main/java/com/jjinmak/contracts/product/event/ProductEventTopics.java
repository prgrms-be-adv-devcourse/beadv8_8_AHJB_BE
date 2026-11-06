package com.jjinmak.contracts.product.event;

public final class ProductEventTopics {

    public static final String PRODUCT_REGISTERED = "product.registered";

    // 상품 등록·수정·삭제 (검색 인덱스 동기화용)
    public static final String PRODUCT_CHANGED = "product.changed";

    private ProductEventTopics() {
    }
}
