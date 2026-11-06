package com.jjinmak.product.product.app;

import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductAuctionTerms;
import com.jjinmak.product.product.out.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// 상품 등록·수정·삭제 후 상품 변경 이벤트를 발행한다. (실제 Kafka 전송은 커밋 후)
@Service
@RequiredArgsConstructor
public class ProductPublishChangedUseCase {

    private final ProductRepository productRepository;
    private final ProductReadThumbnailUseCase productReadThumbnailUseCase;
    private final ApplicationEventPublisher eventPublisher;

    public void publish(Product product, ProductChangeType changeType) {
        // 변경 내용을 먼저 DB에 반영해야 @Version이 올라간 값으로 이벤트를 만들 수 있다.
        productRepository.flush();

        ProductAuctionTerms auctionTerms = product.getAuctionTerms();

        eventPublisher.publishEvent(new ProductChangedEvent(
                product.getId(),
                changeType,
                product.getVersion(),
                product.getSeller().getId(),
                product.getName(),
                product.getCategory().name(),
                product.getManufacturer().name(),
                product.getManufacturerEtc(),
                auctionTerms.getStartPrice(),
                auctionTerms.getInstantWinPrice(),
                product.getAuctionScheduledAt(),
                productReadThumbnailUseCase.findThumbnailUrl(product.getId()),
                LocalDateTime.now()
        ));
    }
}
