package com.jjinmak.back.boundedContext.review.app;

import com.jjinmak.back.boundedContext.review.domain.Review;
import com.jjinmak.back.boundedContext.review.domain.ReviewErrorCode;
import com.jjinmak.back.boundedContext.review.out.ReviewRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewDeleteUseCase {

    private final ReviewRepository reviewRepository;

    // 소프트 삭제. 작성 가능 주문의 작성 완료 표시는 유지되어 다시 작성할 수 없다.
    public void deleteReview(Long writerId, Long reviewId){
        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

        review.delete(writerId, LocalDateTime.now());
    }
}
