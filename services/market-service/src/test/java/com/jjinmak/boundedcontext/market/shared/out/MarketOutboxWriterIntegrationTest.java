package com.jjinmak.boundedcontext.market.shared.out;

import com.jjinmak.boundedcontext.market.auction.out.eventPublisher.EventPublisher;
import com.jjinmak.boundedcontext.market.auction.support.AuctionIntegrationTestSupport;
import com.jjinmak.boundedcontext.market.shared.domain.MarketOutbox;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductWishChangeType;
import com.jjinmak.contracts.product.event.ProductWishChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.LongStream;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;
import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_WISH_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 실제 PostgreSQL에서 경매·상품 이벤트가 상태 변경 트랜잭션과 함께 market 아웃박스에 저장·롤백되는지 확인 (#548)
// 같은 스프링 컨텍스트를 쓰는 다른 테스트(경매 생성·종료)도 아웃박스 행을 남기므로,
// 다른 테스트와 겹치지 않는 키를 쓰고 그 키의 행만 확인한다.
class MarketOutboxWriterIntegrationTest extends AuctionIntegrationTestSupport {

    @Autowired
    private EventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private MarketOutboxRepository marketOutboxRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private List<MarketOutbox> 키로_조회(String topic, long key) {
        return marketOutboxRepository.findAll().stream()
                .filter(outbox -> outbox.getTopic().equals(topic) && outbox.getMessageKey().equals(String.valueOf(key)))
                .toList();
    }

    private AuctionWonEvent 낙찰(long auctionId) {
        return new AuctionWonEvent(auctionId, 10L, 20L, 30L, 15_000L, 3_000L,
                LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("커밋되면 경매·상품 이벤트가 함께 저장되고, 본문은 원래 이벤트로 되돌릴 수 있다. Kafka로 바로 보내지 않는다")
    void committed_savesOutbox() {
        AuctionWonEvent won = 낙찰(990_001L);
        ProductWishChangedEvent wish = new ProductWishChangedEvent(990_101L, 7L, ProductWishChangeType.WISHED, null,
                LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(won);
            eventPublisher.publish(wish);
        });

        assertThat(키로_조회(AUCTION_WON, 990_001L)).singleElement().satisfies(outbox -> {
            assertThat(outbox.getPublishedAt()).isNull();
            assertThat(outbox.getCreatedAt()).isNotNull();
            assertThat(objectMapper.readValue(outbox.getPayload(), AuctionWonEvent.class)).isEqualTo(won);
        });
        assertThat(키로_조회(PRODUCT_WISH_CHANGED, 990_101L)).singleElement().satisfies(outbox ->
                assertThat(objectMapper.readValue(outbox.getPayload(), ProductWishChangedEvent.class)).isEqualTo(wish));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("한 트랜잭션의 이벤트 500건이 저장 순서(발행 순서)대로 한 번에 저장된다")
    void manyEvents_savedInOrder() {
        transactionTemplate.executeWithoutResult(status ->
                LongStream.rangeClosed(991_001L, 991_500L).forEach(id -> eventPublisher.publish(낙찰(id))));

        List<MarketOutbox> saved = marketOutboxRepository.findAll().stream()
                .filter(outbox -> outbox.getMessageKey().startsWith("991"))
                .sorted(java.util.Comparator.comparing(MarketOutbox::getId))
                .toList();
        assertThat(saved).hasSize(500);
        assertThat(saved.getFirst().getMessageKey()).isEqualTo("991001");
        assertThat(saved.getLast().getMessageKey()).isEqualTo("991500");
    }

    @Test
    @DisplayName("롤백되면 아웃박스에 남지 않는다")
    void rolledBack_savesNothing() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(낙찰(990_002L));
            status.setRollbackOnly();
        });

        assertThat(키로_조회(AUCTION_WON, 990_002L)).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행된 이벤트는 저장하지 않는다 (기존 AFTER_COMMIT 발행과 같은 동작)")
    void outsideTransaction_savesNothing() {
        eventPublisher.publish(낙찰(990_004L));

        assertThat(키로_조회(AUCTION_WON, 990_004L)).isEmpty();
    }

    @Test
    @DisplayName("묶음 발행 표시(UPDATE 한 번)한 행은 미발행 조회에서 빠진다")
    void published_excludedFromUnpublished() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(낙찰(990_003L)));
        MarketOutbox saved = 키로_조회(AUCTION_WON, 990_003L).getFirst();

        transactionTemplate.executeWithoutResult(status ->
                marketOutboxRepository.markPublished(List.of(saved.getId()), LocalDateTime.now()));

        assertThat(marketOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc())
                .extracting(MarketOutbox::getId)
                .doesNotContain(saved.getId());
    }
}
