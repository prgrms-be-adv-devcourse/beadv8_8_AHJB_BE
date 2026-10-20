package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 업로드된 상품 이미지. 업로드 직후에는 상품이 없고, 상품 등록/수정 시 연결된다.
// 연결되지 않은 채 오래된 이미지는 정리 스케줄러가 삭제한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_product_image_product_sort", columnList = "product_id, sort_order"))
public class ProductImage extends BaseIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private ProductMember uploader;

    // 업로드 직후에는 null
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    // 저장소 기준 경로 (예: products/{UUID}.jpg). 환경별 접두사(dev/ 등)는 저장소가 붙인다.
    @Column(nullable = false, unique = true)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ProductImageFormat format;

    @Column(nullable = false)
    private long size;

    // 0번이 대표 이미지. 상품에 연결되기 전에는 null
    private Integer sortOrder;

    public ProductImage(ProductMember uploader, String storageKey, ProductImageFormat format, long size) {
        this.uploader = uploader;
        this.storageKey = storageKey;
        this.format = format;
        this.size = size;
    }

    public boolean isUploadedBy(Long memberId) {
        return uploader.getId().equals(memberId);
    }

    // 다른 상품에 이미 연결된 이미지는 쓸 수 없다. (같은 상품이면 순서만 바뀐다)
    public void attach(Product product, int sortOrder) {
        if (this.product != null && !this.product.getId().equals(product.getId())) {
            throw new BusinessException(ProductErrorCode.IMAGE_ALREADY_ATTACHED);
        }
        this.product = product;
        this.sortOrder = sortOrder;
    }

    // 상품 수정으로 빠진 이미지. 연결이 끊기면 정리 대상이 된다.
    public void detach() {
        this.product = null;
        this.sortOrder = null;
    }
}
