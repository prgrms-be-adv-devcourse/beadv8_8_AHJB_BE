package com.jjinmak.product.review.app;

import com.jjinmak.product.review.in.dto.ReviewableOrderDto;
import com.jjinmak.product.review.out.ReviewableOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewReadReviewableOrderUseCase {

    private final ReviewableOrderRepository reviewableOrderRepository;

    // 구매확정했고, 아직 후기를 쓰지 않았고, 작성 기한(30일)이 남은 주문 목록
    public List<ReviewableOrderDto> readReviewableOrders(Long buyerId){
        return reviewableOrderRepository.findReviewable(buyerId, LocalDateTime.now()).stream()
                .map(ReviewableOrderDto::from)
                .toList();
    }
}
