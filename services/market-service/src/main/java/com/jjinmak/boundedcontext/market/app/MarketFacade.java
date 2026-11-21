package com.jjinmak.boundedcontext.market.app;

import com.jjinmak.boundedcontext.market.in.dto.MarketRelistResponseDto;
import com.jjinmak.boundedcontext.market.shared.app.MarketOutboxUseCase;
import com.jjinmak.boundedcontext.market.shared.domain.MarketOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 상품과 경매를 함께 다루는 흐름의 입구
@Service
@RequiredArgsConstructor
public class MarketFacade {

    private final MarketAuctionOpenUseCase marketAuctionOpenUseCase;
    private final MarketRelistUseCase marketRelistUseCase;
    private final MarketOutboxUseCase marketOutboxUseCase;

    @Transactional(readOnly = true)
    public List<Long> findAuctionOpenTargetIds(LocalDateTime now) {
        return marketAuctionOpenUseCase.findTargetIds(now);
    }

    // 상품마다 별도 트랜잭션으로 처리해서, 하나가 실패해도 다른 상품은 경매로 넘어간다.
    @Transactional
    public void openAuction(Long productId, LocalDateTime now) {
        marketAuctionOpenUseCase.openAuction(productId, now);
    }

    // 일괄 열기 (#548): 묶음 하나가 한 트랜잭션. 실패하면 스케줄러가 그 묶음만 건별로 다시 처리한다.
    @Transactional
    public int openAuctions(List<Long> productIds, LocalDateTime now) {
        return marketAuctionOpenUseCase.openAuctions(productIds, now);
    }

    @Transactional
    public MarketRelistResponseDto relist(Long sellerId, Long productId, LocalDateTime now) {
        return marketRelistUseCase.relist(sellerId, productId, now);
    }

    // 아웃박스 (#548): 조회(읽기 전용) → 묶음 전송(트랜잭션 없음, ack 대기 중 커넥션을 잡지 않음) → 묶음 발행 표시(쓰기)
    @Transactional(readOnly = true)
    public List<MarketOutbox> findUnpublishedOutboxes() {
        return marketOutboxUseCase.findUnpublished();
    }

    public List<MarketOutbox> sendOutboxes(List<MarketOutbox> outboxes) {
        return marketOutboxUseCase.send(outboxes);
    }

    @Transactional
    public void markOutboxesPublished(List<MarketOutbox> published) {
        marketOutboxUseCase.markPublished(published);
    }
}
