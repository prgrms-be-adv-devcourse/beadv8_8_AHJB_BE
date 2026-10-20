package com.jjinmak.back.boundedContext.product.out;

import com.jjinmak.back.boundedContext.product.domain.ProductImage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    // 상품의 이미지 (노출 순서)
    @Query("select i from ProductImage i where i.product.id = :productId order by i.sortOrder asc")
    List<ProductImage> findAllByProductId(Long productId);

    // 여러 상품의 대표 이미지 (목록 화면 썸네일)
    @Query("select i from ProductImage i where i.product.id in :productIds and i.sortOrder = 0")
    List<ProductImage> findThumbnails(Collection<Long> productIds);

    // 상품에 연결되지 않은 채 오래된 이미지 (정리 대상)
    @Query("select i.id from ProductImage i where i.product is null and i.createdAt < :before order by i.id")
    List<Long> findUnattachedIdsBefore(LocalDateTime before, Pageable pageable);
}
