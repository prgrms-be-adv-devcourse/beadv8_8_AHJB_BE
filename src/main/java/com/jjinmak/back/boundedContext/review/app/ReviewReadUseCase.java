package com.jjinmak.back.boundedContext.review.app;

import com.jjinmak.back.boundedContext.review.domain.Review;
import com.jjinmak.back.boundedContext.review.in.dto.ReviewDto;
import com.jjinmak.back.boundedContext.review.in.dto.ReviewListResponseDto;
import com.jjinmak.back.boundedContext.review.out.ReviewRepository;
import com.jjinmak.back.shared.review.dto.SellerRatingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewReadUseCase {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviewRepository;

    public ReviewListResponseDto readReceivedReviews(Long memberId, int page, int size){
        return toResponse(reviewRepository.findReceived(memberId, pageRequest(page, size)));
    }

    public ReviewListResponseDto readWrittenReviews(Long memberId, int page, int size){
        return toResponse(reviewRepository.findWritten(memberId, pageRequest(page, size)));
    }

    // 판매자가 받은 후기 수와 평균 평점 (소수점 첫째 자리 반올림)
    public SellerRatingDto getSellerRating(Long sellerId){
        long reviewCount = reviewRepository.countByTargetIdAndDeletedAtIsNull(sellerId);
        Double average = reviewRepository.findAverageRating(sellerId);

        Double ratingAvg = average == null ? null : Math.round(average * 10) / 10.0;

        return new SellerRatingDto(ratingAvg, reviewCount);
    }

    private Pageable pageRequest(int page, int size){
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }

    private ReviewListResponseDto toResponse(Slice<Review> reviews){
        return new ReviewListResponseDto(
                reviews.stream().map(ReviewDto::from).toList(),
                reviews.hasNext()
        );
    }
}
