package com.jjinmak.back.boundedContext.review.in;

import com.jjinmak.back.boundedContext.review.app.ReviewReadUseCase;
import com.jjinmak.back.shared.review.dto.SellerRatingDto;
import com.jjinmak.back.shared.review.port.ReviewReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ReviewFacade를 주입하면 ProductFacade <-> ReviewFacade 순환 의존이 생기므로 조회 UseCase만 직접 사용한다.
@Component
@RequiredArgsConstructor
public class ReviewReadPortAdapter implements ReviewReadPort {

    private final ReviewReadUseCase reviewReadUseCase;

    @Override
    @Transactional(readOnly = true)
    public SellerRatingDto getSellerRating(Long sellerId) {
        return reviewReadUseCase.getSellerRating(sellerId);
    }
}
