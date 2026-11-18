package com.jjinmak.boundedcontext.auction.app;

import com.jjinmak.boundedcontext.auction.domain.Auction;
import com.jjinmak.boundedcontext.market.product.app.ProductFacade;
import com.jjinmak.contracts.product.dto.ProductSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 경매 응답에 붙일 상품 정보(상품명·썸네일)를 상품 컨텍스트에서 읽어 온다.
// 같은 market 컨텍스트이므로 읽기는 ProductFacade 조회 메서드를 바로 호출한다.
@Component
@RequiredArgsConstructor
public class AuctionProductReader {

    private final ProductFacade productFacade;

    // 목록의 상품 정보를 한 번에 조회한다. (상품 id → 요약)
    public Map<Long, ProductSummaryDto> getSummaries(Collection<? extends Auction> auctions) {
        if (auctions.isEmpty()) {
            return Map.of();
        }

        var productIds = auctions.stream().map(Auction::getProductId).collect(Collectors.toSet());

        return productFacade.getProductSummaries(productIds).stream()
                .collect(Collectors.toMap(ProductSummaryDto::productId, Function.identity()));
    }
}
