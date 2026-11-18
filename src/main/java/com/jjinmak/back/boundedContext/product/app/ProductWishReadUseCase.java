package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.ProductWish;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishListResponseDto;
import com.jjinmak.back.boundedContext.product.out.ProductWishRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductWishReadUseCase {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProductWishRepository productWishRepository;
    private final ProductReadThumbnailUseCase productReadThumbnailUseCase;

    // TODO: 현재가, 남은 시간 등 경매 정보는 경매 컨텍스트 조회 포트가 생기면 추가
    public ProductWishListResponseDto readMyWishes(Long memberId, int page, int size){
        Slice<ProductWish> wishes = productWishRepository.findMyWishes(
                memberId, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));

        List<Long> productIds = wishes.stream().map(wish -> wish.getProduct().getId()).toList();
        Map<Long, String> thumbnailUrls = productReadThumbnailUseCase.findThumbnailUrls(productIds);

        return new ProductWishListResponseDto(
                wishes.stream()
                        .map(wish -> ProductWishDto.of(wish, thumbnailUrls.get(wish.getProduct().getId())))
                        .toList(),
                wishes.hasNext()
        );
    }
}
