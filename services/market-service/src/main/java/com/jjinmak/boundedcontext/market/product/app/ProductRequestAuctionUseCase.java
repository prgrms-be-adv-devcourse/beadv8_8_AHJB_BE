package com.jjinmak.boundedcontext.market.product.app;

import com.jjinmak.boundedcontext.market.product.domain.Product;
import com.jjinmak.boundedcontext.market.product.domain.ProductAuctionTerms;
import com.jjinmak.boundedcontext.market.product.domain.ProductErrorCode;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductAuctionOpenDto;
import com.jjinmak.boundedcontext.market.product.out.ProductRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductRequestAuctionUseCase {

    private final ProductRepository productRepository;
    private final ProductReadThumbnailUseCase productReadThumbnailUseCase;
    private final ApplicationEventPublisher eventPublisher;

    public List<Long> findTargetIds(LocalDateTime now){
        return productRepository.findAuctionRequestTargetIds(now);
    }

    // 시작 예정 시각이 된 상품에 요청 표시를 하고, 경매를 열 판매 조건을 돌려준다.
    // 경매 생성은 market 흐름(MarketAuctionOpenUseCase)이 같은 트랜잭션에서 이어서 한다.
    // 상품 등록 이벤트는 정산·채팅이 상품명·썸네일을 저장하는 데 쓰므로 계속 발행한다. (커밋 후 전송)
    public Optional<ProductAuctionOpenDto> prepareAuctionOpen(Long productId, LocalDateTime now){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        // 조회 이후 삭제되었거나 이미 요청된 경우
        if (!product.isAuctionRequestable(now)){
            return Optional.empty();
        }

        product.requestAuction(now);

        publishAuctionRequest(product);
        return Optional.of(ProductAuctionOpenDto.from(product));
    }

    // 상품 등록 이벤트. 경매 생성에는 더 이상 쓰지 않고, 정산·채팅이 상품명·썸네일을 저장하는 데 쓴다.
    // 이벤트 모양은 그대로 유지한다. (시작 시각, 경매 방식별 조건 포함)
    // 옛 필드(instantWinPrice, duration, customEndAt)는 비워 보낸다.
    // 경매 방식에 맞는 조건만 채우고 다른 방식의 조건은 null로 보낸다.
    // - 영국식: english(즉시낙찰가, 종료 시각)
    // - 네덜란드식: dutch(최저가, 하락률, 하락 주기). 종료 시각은 경매가 가격 규칙으로 계산한다.
    private void publishAuctionRequest(Product product){
        ProductAuctionTerms auctionTerms = product.getAuctionTerms();
        LocalDateTime scheduledAt = product.getAuctionScheduledAt();

        ProductRegisteredEvent.EnglishTerms english = null;
        ProductRegisteredEvent.DutchTerms dutch = null;

        if (auctionTerms.isDutch()){
            dutch = new ProductRegisteredEvent.DutchTerms(
                    auctionTerms.getFloorPrice(),
                    auctionTerms.getDropRatePercent(),
                    auctionTerms.getDropIntervalSec());
        } else {
            english = new ProductRegisteredEvent.EnglishTerms(
                    auctionTerms.getInstantWinPrice(),
                    auctionTerms.resolveEndAt(scheduledAt));
        }

        eventPublisher.publishEvent(new ProductRegisteredEvent(
                product.getId(),
                product.getSeller().getId(),
                product.getShippingFee(),
                auctionTerms.getStartPrice(),
                null,
                0,
                null,
                product.getName(),
                product.getCategory().name(),
                productReadThumbnailUseCase.findThumbnailUrl(product.getId()),
                auctionTerms.getAuctionType().name(),
                scheduledAt,
                english,
                dutch
        ));
    }
}
