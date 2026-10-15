package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.ProductWish;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishListResponseDto;
import com.jjinmak.back.boundedContext.product.out.ProductWishRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductWishReadUseCase {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProductWishRepository productWishRepository;

    // TODO: 현재가, 남은 시간 등 경매 정보는 경매 컨텍스트 조회 포트가 생기면 추가
    public ProductWishListResponseDto readMyWishes(Long memberId, int page, int size){
        Slice<ProductWish> wishes = productWishRepository.findMyWishes(
                memberId, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));

        return new ProductWishListResponseDto(
                wishes.stream().map(ProductWishDto::from).toList(),
                wishes.hasNext()
        );
    }
}
