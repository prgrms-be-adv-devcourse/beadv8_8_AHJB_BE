package com.jjinmak.back.boundedContext.review.in;

import com.jjinmak.back.boundedContext.review.app.ReviewFacade;
import com.jjinmak.back.boundedContext.review.in.dto.*;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
public class ReviewApiController {

    private final ReviewFacade reviewFacade;

    // 조회는 로그인만 하면 가능하다.
    @GetMapping("/reviewable-orders")
    public ResponseEntity<RsData<List<ReviewableOrderDto>>> readReviewableOrders(@AuthenticationPrincipal AuthPrincipal principal){
        List<ReviewableOrderDto> response = reviewFacade.readReviewableOrders(principal.memberId());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    // 후기 작성/수정/삭제는 정지되지 않은 회원만 가능하다. (PERM_TRADE)
    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @PostMapping
    public ResponseEntity<RsData<ReviewDto>> createReview(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @Valid @RequestBody ReviewCreateRequestDto request){
        ReviewDto response = reviewFacade.createReview(principal.memberId(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(response));
    }

    @GetMapping("/received")
    public ResponseEntity<RsData<ReviewListResponseDto>> readReceivedReviews(@AuthenticationPrincipal AuthPrincipal principal,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "10") int size){
        ReviewListResponseDto response = reviewFacade.readReceivedReviews(principal.memberId(), page, size);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    @GetMapping("/written")
    public ResponseEntity<RsData<ReviewListResponseDto>> readWrittenReviews(@AuthenticationPrincipal AuthPrincipal principal,
                                                                           @RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "10") int size){
        ReviewListResponseDto response = reviewFacade.readWrittenReviews(principal.memberId(), page, size);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @PatchMapping("/{reviewId}")
    public ResponseEntity<Void> updateReview(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable Long reviewId,
                                             @Valid @RequestBody ReviewUpdateRequestDto request){
        reviewFacade.updateReview(principal.memberId(), reviewId, request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable Long reviewId){
        reviewFacade.deleteReview(principal.memberId(), reviewId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
