package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_FAILED;
import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_STARTED;
import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;

// 경매 시작·낙찰·유찰 이벤트를 커밋 직전(BEFORE_COMMIT)에 아웃박스에 저장한다 (#548).
// 상태 변경과 같은 트랜잭션이라 함께 커밋·롤백된다. 토픽과 키는 기존 발행기(auctionId)와 같다.
// 입찰 성공·마감 임박은 이번 범위가 아니라 AuctionKafkaEventPublisher에서 바로 보낸다.
@Component
@RequiredArgsConstructor
public class AuctionOutboxWriter {

    private final AuctionOutboxRepository auctionOutboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(AuctionStartedEvent event){
        save(AUCTION_STARTED, event.auctionId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(AuctionWonEvent event){
        save(AUCTION_WON, event.auctionId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(AuctionFailedEvent event){
        save(AUCTION_FAILED, event.auctionId(), event);
    }

    private void save(String topic, Long key, Object event){
        auctionOutboxRepository.save(new AuctionOutbox(topic, String.valueOf(key), objectMapper.writeValueAsString(event)));
    }
}
