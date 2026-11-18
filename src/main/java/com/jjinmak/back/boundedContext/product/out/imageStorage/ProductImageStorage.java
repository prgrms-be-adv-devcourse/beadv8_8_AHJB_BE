package com.jjinmak.back.boundedContext.product.out.imageStorage;

// 상품 이미지 파일 저장소. dev 서버는 S3, 로컬/테스트는 로컬 구현을 쓴다. (product-image.storage)
public interface ProductImageStorage {

    // key: 저장소 기준 경로 (예: products/{UUID}.jpg). 환경별 접두사는 구현체가 붙인다.
    void upload(String key, byte[] bytes, String contentType);

    void delete(String key);

    // 브라우저가 이미지를 볼 수 있는 URL
    String urlOf(String key);
}
