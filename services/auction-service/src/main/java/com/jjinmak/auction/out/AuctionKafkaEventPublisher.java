package com.jjinmak.auction.out;

import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.*;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class AuctionKafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void publish(AuctionStartedEvent event) {
        kafkaTemplate.send(AUCTION_STARTED, String.valueOf(event.auctionId()), event);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void publish(AuctionWonEvent event) {
        kafkaTemplate.send(AUCTION_WON, String.valueOf(event.auctionId()), event);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void publish(AuctionFailedEvent event) {
        kafkaTemplate.send(AUCTION_FAILED, String.valueOf(event.auctionId()), event);
    }
}