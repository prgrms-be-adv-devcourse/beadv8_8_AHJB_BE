package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
import com.jjinmak.boundedcontext.market.auction.out.eventPublisher.EventPublisher;
import com.jjinmak.boundedcontext.market.auction.support.AuctionIntegrationTestSupport;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 실제 PostgreSQL에서 경매 이벤트가 상태 변경 트랜잭션과 함께 아웃박스에 저장·롤백되는지 확인 (#548)
// 같은 스프링 컨텍스트를 쓰는 다른 테스트(경매 생성·종료)도 아웃박스 행을 남기므로,
// 다른 테스트와 겹치지 않는 경매 ID를 쓰고 그 키의 행만 확인한다.
class AuctionOutboxWriterIntegrationTest extends AuctionIntegrationTestSupport {

    @Autowired
    private EventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private AuctionOutboxRepository auctionOutboxRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        auctionOutboxRepository.deleteAllInBatch();
    }

    private List<AuctionOutbox> 키로_조회(long auctionId) {
        return auctionOutboxRepository.findAll().stream()
                .filter(outbox -> outbox.getMessageKey().equals(String.valueOf(auctionId)))
                .toList();
    }

    private AuctionWonEvent 낙찰(long auctionId) {
        return new AuctionWonEvent(auctionId, 10L, 20L, 30L, 15_000L, 3_000L,
                LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("커밋되면 아웃박스에 저장되고, 본문은 원래 이벤트로 되돌릴 수 있다. Kafka로 바로 보내지 않는다")
    void committed_savesOutbox() {
        AuctionWonEvent event = 낙찰(990_001L);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertThat(키로_조회(990_001L))
                .singleElement()
                .satisfies(outbox -> {
                    assertThat(outbox.getTopic()).isEqualTo(AUCTION_WON);
                    assertThat(outbox.getPublishedAt()).isNull();
                    assertThat(objectMapper.readValue(outbox.getPayload(), AuctionWonEvent.class)).isEqualTo(event);
                });
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("롤백되면 아웃박스에 남지 않는다")
    void rolledBack_savesNothing() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(낙찰(990_002L));
            status.setRollbackOnly();
        });

        assertThat(키로_조회(990_002L)).isEmpty();
    }

    @Test
    @DisplayName("묶음 발행 표시(UPDATE 한 번)한 행은 미발행 조회에서 빠진다")
    void published_excludedFromUnpublished() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(낙찰(990_003L)));
        AuctionOutbox saved = 키로_조회(990_003L).getFirst();

        transactionTemplate.executeWithoutResult(status ->
                auctionOutboxRepository.markPublished(List.of(saved.getId()), LocalDateTime.now()));

        assertThat(auctionOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc())
                .extracting(AuctionOutbox::getId)
                .doesNotContain(saved.getId());
    }
}
