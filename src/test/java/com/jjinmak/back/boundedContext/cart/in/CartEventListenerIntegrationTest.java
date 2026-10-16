package com.jjinmak.back.boundedContext.cart.in;

import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CartEventListenerIntegrationTest {

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    CartMemberRepository cartMemberRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    LocalDateTime winAt = LocalDateTime.now();

    @AfterEach
    void tearDown() {
        cartItemRepository.deleteAllInBatch();
        cartMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("트랜잭션 안에서 낙찰 이벤트를 발행하면, 커밋 뒤 장바구니 항목이 생긴다")
    void auctionWon_insideTransaction_createsItem() {
        syncMembers();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(wonEvent(10L)));

        assertThat(cartItemRepository.findByAuctionId(10L)).isPresent();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행한 낙찰 이벤트는 리스너가 받지 않는다")
    void auctionWon_outsideTransaction_ignored() {
        syncMembers();

        eventPublisher.publish(wonEvent(10L));

        assertThat(cartItemRepository.findByAuctionId(10L)).isEmpty();
    }

    @Test
    @DisplayName("같은 낙찰 이벤트가 두 번 와도 장바구니 항목은 1개다")
    void auctionWon_twice_onlyOneItem() {
        syncMembers();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(wonEvent(10L)));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(wonEvent(10L)));

        assertThat(cartItemRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원 동기화보다 낙찰 이벤트가 먼저 오면 장바구니 항목이 생기지 않는다 (현재 동작)")
    void auctionWon_beforeMemberSync_noItem() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(wonEvent(10L)));

        assertThat(cartItemRepository.findByAuctionId(10L)).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 안에서 결제 성공 이벤트를 발행하면, 커밋 뒤 그 경매의 장바구니 항목이 지워진다")
    void orderPaymentSucceed_deletesItem() {
        syncMembers();
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(wonEvent(10L)));

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publish(new OrderPaymentSucceedEvent(5L, 10L, 20L, LocalDateTime.now())));

        assertThat(cartItemRepository.findByAuctionId(10L)).isEmpty();
    }

    private void syncMembers() {
        cartMemberRepository.save(new CartMember(1L, UUID.randomUUID(), "낙찰자"));
        cartMemberRepository.save(new CartMember(2L, UUID.randomUUID(), "판매자"));
    }

    private AuctionWonEvent wonEvent(Long auctionId) {
        return new AuctionWonEvent(auctionId, 20L, 1L, 2L, 30_000L, 3_000L, winAt);
    }
}
