package com.jjinmak.back.boundedContext.review.out;

import com.jjinmak.back.boundedContext.review.domain.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByIdAndDeletedAtIsNull(Long id);

    // 내가 판매자로서 받은 후기 (최신순)
    @Query("""
            select r from Review r
            join fetch r.reviewableOrder join fetch r.writer join fetch r.target
            where r.target.id = :targetId and r.deletedAt is null
            order by r.id desc
            """)
    Slice<Review> findReceived(Long targetId, Pageable pageable);

    // 내가 쓴 후기 (최신순)
    @Query("""
            select r from Review r
            join fetch r.reviewableOrder join fetch r.writer join fetch r.target
            where r.writer.id = :writerId and r.deletedAt is null
            order by r.id desc
            """)
    Slice<Review> findWritten(Long writerId, Pageable pageable);
}
