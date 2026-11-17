package com.jjinmak.product.product.out;

import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductAuctionStatus;
import com.jjinmak.contracts.product.domain.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 경매를 요청했는데 아직 경매가 시작되지 않은 상품 (요청 이벤트가 유실된 경우, 재전송 대상)
    // - 요청 시각이 from ~ to 사이인 상품만 본다. (to: 경매가 만들어질 시간을 기다려 준다, from: 계속 거절되는 상품은 포기한다)
    // - 시작 이벤트보다 결과 이벤트가 먼저 온 상품은 경매가 있으므로 제외한다. (상태가 SCHEDULED 또는 비어 있는 상품만)
    @Query("""
            select p.id from Product p
            where p.deletedAt is null
              and p.currentAuctionId is null
              and p.auctionRequestedAt between :from and :to
              and (p.auctionState.status is null or p.auctionState.status = :scheduled)
            order by p.id
            """)
    List<Long> findAuctionResendTargetIds(LocalDateTime from, LocalDateTime to, ProductAuctionStatus scheduled);

    // 곧 시작: 아직 경매로 넘어가지 않은 상품 (시작 임박순). category가 null이면 전체
    @Query(value = """
            select p from Product p
            where p.auctionRequestedAt is null
              and p.deletedAt is null
              and (:category is null or p.category = :category)
            order by p.auctionScheduledAt asc, p.id asc
            """,
            countQuery = """
            select count(p) from Product p
            where p.auctionRequestedAt is null
              and p.deletedAt is null
              and (:category is null or p.category = :category)
            """)
    Page<Product> findUpcoming(ProductCategory category, Pageable pageable);

    // 내 상품: 판매자가 등록한 상품 (삭제 제외, 최신 등록순)
    @Query("""
            select p from Product p
            where p.seller.id = :sellerId
              and p.deletedAt is null
            order by p.id desc
            """)
    List<Product> findMine(Long sellerId);

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
