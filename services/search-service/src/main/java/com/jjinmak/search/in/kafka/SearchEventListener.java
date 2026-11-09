package com.jjinmak.search.in.kafka;

import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.contracts.product.event.ProductEventTopics;
import com.jjinmak.search.app.ProductSearchSyncUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 상품·경매 이벤트를 받아 검색 문서에 반영한다.
// 처리에 실패하면 3번 재시도(1·2·4초) 후 "토픽-dlt"로 보낸다. (common-kafka의 에러 처리기)
@Component
@RequiredArgsConstructor
public class SearchEventListener {

    private final ProductSearchSyncUseCase productSearchSyncUseCase;

    // 상품 등록·수정·삭제 → 상품 정보 반영 (곧 시작 상품도 검색되도록)
    @KafkaListener(topics = ProductEventTopics.PRODUCT_CHANGED, groupId = "search")
    public void handle(ProductChangedEvent event) {
        productSearchSyncUseCase.apply(event);
    }

    // 경매 시작 → 진행 중
    @KafkaListener(topics = AuctionEventTopics.AUCTION_STARTED, groupId = "search")
    public void handle(AuctionStartedEvent event) {
        productSearchSyncUseCase.apply(event);
    }

    // 낙찰 → SOLD
    @KafkaListener(topics = AuctionEventTopics.AUCTION_WON, groupId = "search")
    public void handle(AuctionWonEvent event) {
        productSearchSyncUseCase.apply(event);
    }

    // 무산(유찰·미결제·환불) → FAILED
    @KafkaListener(topics = AuctionEventTopics.AUCTION_FAILED, groupId = "search")
    public void handle(AuctionFailedEvent event) {
        productSearchSyncUseCase.apply(event);
    }
}
