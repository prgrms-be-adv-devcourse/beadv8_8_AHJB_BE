package com.jjinmak.back.boundedContext.product.out;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductQna;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductQnaRepository extends JpaRepository<ProductQna, Long> {

    Optional<ProductQna> findByIdAndDeletedAtIsNull(Long id);

    // 상품의 질문 목록 (최신순)
    @Query("select q from ProductQna q join fetch q.writer where q.product = :product and q.parent is null order by q.id desc")
    Slice<ProductQna> findQuestions(Product product, Pageable pageable);

    // 질문들에 달린 답글 (작성순)
    @Query("select q from ProductQna q join fetch q.writer where q.parent.id in :parentIds order by q.id asc")
    List<ProductQna> findReplies(Collection<Long> parentIds);

    // 폴링용: after 이후에 작성된 질문/답글 (작성순)
    @Query("select q from ProductQna q join fetch q.writer where q.product = :product and q.id > :after order by q.id asc")
    List<ProductQna> findCreatedAfter(Product product, Long after, Pageable pageable);

    @Query("select max(q.id) from ProductQna q where q.product = :product")
    Long findLatestId(Product product);
}
