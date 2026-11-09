package com.jjinmak.search.app;

import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.search.domain.ProductSearchDocument;
import com.jjinmak.search.out.elasticsearch.ProductSearchStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// 상품·경매 이벤트를 받아 검색 문서를 최신 상태로 맞춘다.
// 규칙(오래된 이벤트 무시, 확정 상태 유지 등)은 ProductSearchDocument에 있다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchSyncUseCase {

    private final ProductSearchStore store;

    public void apply(ProductChangedEvent event) {
        if (event.version() == null || event.changeType() == null) {
            throw new InvalidEventException("상품 변경 이벤트에 버전이나 변경 종류가 없습니다. productId=" + event.productId());
        }
        sync(event.productId(), event, ProductSearchDocument::applyProductChange);
    }

    public void apply(AuctionStartedEvent event) {
        sync(event.productId(), event, ProductSearchDocument::applyAuctionStarted);
    }

    public void apply(AuctionWonEvent event) {
        sync(event.productId(), event, ProductSearchDocument::applyAuctionWon);
    }

    public void apply(AuctionFailedEvent event) {
        sync(event.productId(), event, ProductSearchDocument::applyAuctionFailed);
    }

    // 문서를 읽고(없으면 빈 문서) → 규칙대로 고치고 → 바뀌었으면 저장한다.
    // 같은 문서를 다른 이벤트가 먼저 저장했으면 저장이 실패하고, Kafka가 이 이벤트를 다시 처리한다.
    private <E> void sync(Long productId, E event, Changer<E> changer) {
        if (productId == null) {
            throw new InvalidEventException("이벤트에 상품 id가 없습니다: " + event);
        }

        ProductSearchDocument document = store.findById(productId)
                .orElseGet(() -> ProductSearchDocument.empty(productId));

        if (!changer.apply(document, event, LocalDateTime.now())) {
            log.info("검색 문서에 반영하지 않음 (오래되었거나 이미 끝난 경매의 이벤트): productId={}, event={}", productId, event);
            return;
        }

        store.save(document);
    }

    @FunctionalInterface
    private interface Changer<E> {
        boolean apply(ProductSearchDocument document, E event, LocalDateTime now);
    }
}
