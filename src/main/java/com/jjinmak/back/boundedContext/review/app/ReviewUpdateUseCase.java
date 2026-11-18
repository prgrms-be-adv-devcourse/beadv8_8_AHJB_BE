package com.jjinmak.back.boundedContext.review.app;

import com.jjinmak.back.boundedContext.review.domain.Review;
import com.jjinmak.back.boundedContext.review.domain.ReviewErrorCode;
import com.jjinmak.back.boundedContext.review.in.dto.ReviewUpdateRequestDto;
import com.jjinmak.back.boundedContext.review.out.ReviewRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewUpdateUseCase {

    private final ReviewRepository reviewRepository;

    public void updateReview(Long writerId, Long reviewId, ReviewUpdateRequestDto request){
        Review review = reviewRepository.findByIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

        review.update(writerId, request.rating(), request.content());
    }
}
