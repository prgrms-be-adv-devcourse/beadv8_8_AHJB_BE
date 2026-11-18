package com.jjinmak.back.boundedContext.review.app;

import com.jjinmak.back.boundedContext.review.domain.ReviewMember;
import com.jjinmak.back.boundedContext.review.in.dto.*;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewFacade {

    private final ReviewSyncMemberUseCase reviewSyncMemberUseCase;
    private final ReviewCreateReviewableOrderUseCase reviewCreateReviewableOrderUseCase;
    private final ReviewReadReviewableOrderUseCase reviewReadReviewableOrderUseCase;
    private final ReviewCreateUseCase reviewCreateUseCase;
    private final ReviewReadUseCase reviewReadUseCase;
    private final ReviewUpdateUseCase reviewUpdateUseCase;
    private final ReviewDeleteUseCase reviewDeleteUseCase;

    @Transactional
    public ReviewMember syncMember(MemberDto memberDto){
        return reviewSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public void createReviewableOrder(OrderConfirmPurchaseEvent event){
        reviewCreateReviewableOrderUseCase.createReviewableOrder(event);
    }

    @Transactional(readOnly = true)
    public List<ReviewableOrderDto> readReviewableOrders(Long buyerId){
        return reviewReadReviewableOrderUseCase.readReviewableOrders(buyerId);
    }

    @Transactional
    public ReviewDto createReview(Long writerId, ReviewCreateRequestDto request){
        return reviewCreateUseCase.createReview(writerId, request);
    }

    @Transactional(readOnly = true)
    public ReviewListResponseDto readReceivedReviews(Long memberId, int page, int size){
        return reviewReadUseCase.readReceivedReviews(memberId, page, size);
    }

    @Transactional(readOnly = true)
    public ReviewListResponseDto readWrittenReviews(Long memberId, int page, int size){
        return reviewReadUseCase.readWrittenReviews(memberId, page, size);
    }

    @Transactional
    public void updateReview(Long writerId, Long reviewId, ReviewUpdateRequestDto request){
        reviewUpdateUseCase.updateReview(writerId, reviewId, request);
    }

    @Transactional
    public void deleteReview(Long writerId, Long reviewId){
        reviewDeleteUseCase.deleteReview(writerId, reviewId);
    }
}
