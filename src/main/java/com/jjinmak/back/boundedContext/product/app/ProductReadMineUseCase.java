package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.in.dto.ProductMyItemDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductMyListResponseDto;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductReadMineUseCase {

    private final ProductRepository productRepository;
    private final ProductReadThumbnailUseCase productReadThumbnailUseCase;

    // 내가 등록한 상품(삭제 제외)을 최신 등록순으로 조회한다.
    // 탭(시작 전/진행 중/종료)별 개수는 프론트가 status로 센다. 상품이 많아지면 페이지·상태 필터를 추가한다.
    public ProductMyListResponseDto readMine(Long sellerId){
        LocalDateTime now = LocalDateTime.now();
        List<Product> products = productRepository.findMine(sellerId);

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, String> thumbnailUrls = productReadThumbnailUseCase.findThumbnailUrls(productIds);

        List<ProductMyItemDto> content = products.stream()
                .map(product -> ProductMyItemDto.of(product, thumbnailUrls.get(product.getId()), now))
                .toList();

        return new ProductMyListResponseDto(content, now);
    }
}
