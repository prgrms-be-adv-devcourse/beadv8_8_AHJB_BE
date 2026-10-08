package com.jjinmak.back.boundedContext.product.out;

import com.jjinmak.back.boundedContext.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 수정 기간이 끝났지만 아직 경매를 요청하지 않은 상품
    @Query("""
            select p.id from Product p
            where p.auctionRequestedAt is null
              and p.deletedAt is null
              and p.createdAt <= :editableUntil
            order by p.id
            """)
    List<Long> findAuctionRequestTargetIds(LocalDateTime editableUntil);
}
