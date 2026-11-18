package com.jjinmak.boundedcontext.market.review.app;

import com.jjinmak.boundedcontext.market.review.domain.Review;
import com.jjinmak.boundedcontext.market.review.domain.ReviewErrorCode;
import com.jjinmak.boundedcontext.market.review.in.dto.ReviewUpdateRequestDto;
import com.jjinmak.boundedcontext.market.review.out.ReviewRepository;
import com.jjinmak.common.exception.BusinessException;
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
