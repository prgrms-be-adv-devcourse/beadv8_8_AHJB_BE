package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.contracts.auction.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.*;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

// 커밋 후 Kafka로 바로 보내는 이벤트.
// 경매 시작·낙찰·유찰은 outbox로 옮겼다 (AuctionOutboxWriter, #548)
@Component
@RequiredArgsConstructor
public class AuctionKafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void publish(AuctionBidSucceedEvent event) {
        kafkaTemplate.send(AUCTION_BID_SUCCEED, String.valueOf(event.auctionId()), event);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void publish(AuctionEndingSoonEvent event) {
        kafkaTemplate.send(AUCTION_ENDING_SOON, String.valueOf(event.auctionId()), event);
    }
}
