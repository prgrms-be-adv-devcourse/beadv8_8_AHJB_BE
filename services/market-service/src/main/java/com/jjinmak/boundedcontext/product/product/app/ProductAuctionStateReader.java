package com.jjinmak.boundedcontext.product.product.app;

import com.jjinmak.boundedcontext.auction.app.AuctionQueryFacade;
import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.auction.dto.AuctionStateDto;
import com.jjinmak.boundedcontext.product.product.domain.Product;
import com.jjinmak.boundedcontext.product.product.domain.ProductAuctionFailureReason;
import com.jjinmak.boundedcontext.product.product.domain.ProductAuctionStatus;
import com.jjinmak.boundedcontext.product.product.in.dto.ProductAuctionStateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// 상품들의 현재 회차 경매 상태를 경매 컨텍스트에서 한 번에 읽어 온다. (상태는 경매에만 있다)
// 같은 market 컨텍스트이므로 읽기는 경매의 조회 전용 Facade를 바로 호출한다.
@Component
@RequiredArgsConstructor
public class ProductAuctionStateReader {

    private final AuctionQueryFacade auctionQueryFacade;

    // 상품 id → 경매 상태
    public Map<Long, ProductAuctionStateDto> read(Collection<Product> products) {
        var auctionIds = products.stream()
                .map(Product::getCurrentAuctionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, AuctionStateDto> auctions = auctionQueryFacade.getAuctionStates(auctionIds);

        return products.stream().collect(Collectors.toMap(Product::getId,
                product -> toProductState(product.getCurrentAuctionId(), auctions), (a, b) -> a));
    }

    private ProductAuctionStateDto toProductState(Long auctionId, Map<Long, AuctionStateDto> auctions) {
        if (auctionId == null) {
            return ProductAuctionStateDto.scheduled();
        }
        AuctionStateDto auction = auctions.get(auctionId);
        if (auction == null) {
            // 상품이 가리키는 경매가 없을 수 없지만, 있더라도 목록 전체가 깨지지 않게 시작 전으로 보여 준다.
            return ProductAuctionStateDto.scheduled();
        }
        return new ProductAuctionStateDto(
                toProductStatus(auction.status()),
                auction.endAt(),
                auction.failureReason() == null ? null : ProductAuctionFailureReason.from(auction.failureReason().name())
        );
    }

    // 결제완료·구매확정은 주문의 관심사라 상품에서는 낙찰(SOLD)로 묶는다.
    private ProductAuctionStatus toProductStatus(AuctionStatus status) {
        return switch (status) {
            case IN_PROGRESS -> ProductAuctionStatus.IN_PROGRESS;
            case WON, PAID, COMPLETED -> ProductAuctionStatus.SOLD;
            case FAILED -> ProductAuctionStatus.FAILED;
        };
    }
}
