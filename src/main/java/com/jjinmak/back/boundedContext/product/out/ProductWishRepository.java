package com.jjinmak.back.boundedContext.product.out;

import com.jjinmak.back.boundedContext.product.domain.ProductWish;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductWishRepository extends JpaRepository<ProductWish, Long> {

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    Optional<ProductWish> findByMemberIdAndProductId(Long memberId, Long productId);

    // 내가 찜한 상품 (최근 찜한 순). 삭제된 상품은 제외한다.
    @Query("""
            select w from ProductWish w join fetch w.product p
            where w.member.id = :memberId and p.deletedAt is null
            order by w.id desc
            """)
    Slice<ProductWish> findMyWishes(Long memberId, Pageable pageable);
}
