package com.jjinmak.back.boundedContext.product.out;

import com.jjinmak.back.boundedContext.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 삭제되지 않은 상품만 조회
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    // 시작 예정 시각이 되었지만 아직 경매를 요청하지 않은 상품
    @Query("""
            select p.id from Product p
            where p.auctionRequestedAt is null
              and p.deletedAt is null
              and p.auctionScheduledAt <= :now
            order by p.id
            """)
    List<Long> findAuctionRequestTargetIds(LocalDateTime now);

    // 찜 수는 동시 요청과 @Version 충돌을 피하기 위해 DB에서 직접 증감한다.
    @Modifying
    @Query("update Product p set p.wishCount = p.wishCount + 1 where p.id = :productId")
    int increaseWishCount(Long productId);

    @Modifying
    @Query("update Product p set p.wishCount = p.wishCount - 1 where p.id = :productId and p.wishCount > 0")
    int decreaseWishCount(Long productId);

    @Query("select p.wishCount from Product p where p.id = :productId")
    int findWishCount(Long productId);
}
