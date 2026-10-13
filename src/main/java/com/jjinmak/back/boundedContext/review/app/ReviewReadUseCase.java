package com.jjinmak.back.boundedContext.review.app;

import com.jjinmak.back.boundedContext.review.domain.Review;
import com.jjinmak.back.boundedContext.review.in.dto.ReviewDto;
import com.jjinmak.back.boundedContext.review.in.dto.ReviewListResponseDto;
import com.jjinmak.back.boundedContext.review.out.ReviewRepository;
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
