package com.jjinmak.product.review.app;

import com.jjinmak.product.review.domain.Review;
import com.jjinmak.product.review.domain.ReviewErrorCode;
import com.jjinmak.product.review.domain.ReviewableOrder;
import com.jjinmak.product.review.in.dto.ReviewCreateRequestDto;
import com.jjinmak.product.review.in.dto.ReviewDto;
import com.jjinmak.product.review.out.ReviewRepository;
import com.jjinmak.product.review.out.ReviewableOrderRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewCreateUseCase {

    private final ReviewableOrderRepository reviewableOrderRepository;
    private final ReviewRepository reviewRepository;

    // 작성 가능 주문(구매자 본인, 미작성, 기한 내)인지 확인 후 후기를 저장한다.
    public ReviewDto createReview(Long writerId, ReviewCreateRequestDto request){
        ReviewableOrder reviewableOrder = reviewableOrderRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEWABLE_ORDER_NOT_FOUND));

        Review review = Review.create(
                reviewableOrder, writerId, request.rating(), request.content(), LocalDateTime.now());

        reviewRepository.save(review);

        return ReviewDto.from(review);
    }
}
