package com.jjinmak.back.boundedContext.review.in;

import com.jjinmak.back.boundedContext.review.app.ReviewFacade;
import com.jjinmak.back.boundedContext.review.in.dto.*;
import com.jjinmak.back.global.rsData.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
public class ReviewApiController {

    private final ReviewFacade reviewFacade;

    // TODO: 인증 방식이 정해진 후에, 접속 유저 정보 가져오기
    private final Long memberDev = 1L;

    @GetMapping("/reviewable-orders")
    public ResponseEntity<RsData<List<ReviewableOrderDto>>> readReviewableOrders(){
        List<ReviewableOrderDto> response = reviewFacade.readReviewableOrders(memberDev);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    @PostMapping
    public ResponseEntity<RsData<ReviewDto>> createReview(@Valid @RequestBody ReviewCreateRequestDto request){
        ReviewDto response = reviewFacade.createReview(memberDev, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(response));
    }

    @GetMapping("/received")
    public ResponseEntity<RsData<ReviewListResponseDto>> readReceivedReviews(@RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "10") int size){
        ReviewListResponseDto response = reviewFacade.readReceivedReviews(memberDev, page, size);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    @GetMapping("/written")
    public ResponseEntity<RsData<ReviewListResponseDto>> readWrittenReviews(@RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "10") int size){
        ReviewListResponseDto response = reviewFacade.readWrittenReviews(memberDev, page, size);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    @PatchMapping("/{reviewId}")
    public ResponseEntity<Void> updateReview(@PathVariable Long reviewId,
                                             @Valid @RequestBody ReviewUpdateRequestDto request){
        reviewFacade.updateReview(memberDev, reviewId, request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId){
        reviewFacade.deleteReview(memberDev, reviewId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
