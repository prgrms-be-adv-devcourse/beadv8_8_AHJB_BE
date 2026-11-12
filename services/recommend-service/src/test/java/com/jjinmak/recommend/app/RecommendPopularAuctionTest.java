package com.jjinmak.recommend.app;

import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.app.dto.AuctionResponseDto;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.out.InteractionLogRepository;
import com.jjinmak.recommend.out.InteractionRepository;
import com.jjinmak.recommend.out.ProductEmbeddingRepository;
import com.jjinmak.recommend.out.RecommendAuctionRepository;
import com.jjinmak.recommend.out.RecommendMemberRepository;
import com.jjinmak.recommend.out.RecommendProductRepository;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static com.jjinmak.contracts.product.event.ProductChangeType.DELETED;
import static org.assertj.core.api.Assertions.assertThat;

class RecommendPopularAuctionTest extends RecommendIntegrationTestSupport {

    static final long SELLER_ID = 9L;
    static final long START_PRICE = 10000L;
    static final String CATEGORY = "ACCESSORY";

    @Autowired
    RecommendFacade recommendFacade;

    @Autowired
    RecommendKafkaListener recommendKafkaListener;

    @Autowired
    RecommendAuctionRepository recommendAuctionRepository;

    @Autowired
    RecommendProductRepository recommendProductRepository;

    @Autowired
    RecommendMemberRepository recommendMemberRepository;

    @Autowired
    InteractionRepository interactionRepository;

    @Autowired
    InteractionLogRepository interactionLogRepository;

    @Autowired
    ProductEmbeddingRepository productEmbeddingRepository;

    LocalDateTime now;

    long bidSequence;

    @BeforeEach
    void setUp() {
        clean();
        now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        bidSequence = 0;
        LongStream.rangeClosed(1, 5)
                .forEach(memberId -> recommendMemberRepository.save(new RecommendMember(memberId, UUID.randomUUID(), "회원" + memberId)));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("입찰한 회원 수 점수와 마감 임박 점수를 0.7 대 0.3으로 더한 값이 큰 순서로 진행 중인 경매를 돌려준다")
    void popular_ordersByBiddersAndUrgency() {
        auction(3001L, 301L, now.plusHours(72), 1L, 2L, 3L);
        auction(3002L, 302L, now.plusHours(2), 1L);
        auction(3003L, 303L, now.plusHours(1));
        auction(3004L, 304L, now.plusHours(120), 1L, 2L);
        auction(3005L, 305L, now.plusHours(73));

        assertThat(auctionIds(recommendFacade.readPopularAuctionList()))
                .containsExactly(3001L, 3002L, 3004L, 3003L, 3005L);
    }

    @Test
    @DisplayName("응답 항목에는 경매 id, 상품 id, 카테고리, 현재가, 종료 시각이 들어간다")
    void popular_mapsAuctionFields() {
        auction(3001L, 301L, now.plusHours(72), 1L);

        assertThat(recommendFacade.readPopularAuctionList())
                .containsExactly(new AuctionResponseDto(3001L, 301L, CATEGORY, START_PRICE, now.plusHours(72)));
    }

    @Test
    @DisplayName("끝난 경매, 마감 시각이 지난 경매, 삭제된 상품의 경매는 입찰한 회원이 더 많아도 빠지고, 인기 점수의 기준값에도 쓰이지 않는다")
    void popular_excludesClosedAuctionsFromResultAndScale() {
        auction(3001L, 301L, now.plusHours(72), 1L, 2L, 3L);
        auction(3002L, 302L, now.plusHours(2), 1L);
        auction(3006L, 306L, now.plusHours(72), 1L, 2L, 3L, 4L, 5L);
        recommendKafkaListener.handle(new AuctionFailedEvent(3006L, 306L, "NO_WINNER", now));
        auction(3007L, 307L, now.minusHours(1), 1L, 2L, 3L, 4L, 5L);
        auction(3008L, 308L, now.plusHours(72), 1L, 2L, 3L, 4L, 5L);
        recommendKafkaListener.handle(productEvent(308L, DELETED, 1L));

        assertThat(auctionIds(recommendFacade.readPopularAuctionList()))
                .containsExactly(3001L, 3002L);
    }

    @Test
    @DisplayName("한 회원이 같은 경매에 여러 번 입찰해도 입찰한 회원 수는 한 명으로 센다")
    void popular_countsBiddersNotBids() {
        auction(3001L, 301L, now.plusHours(72), 1L, 1L, 1L);
        auction(3002L, 302L, now.plusHours(72), 1L, 2L);

        assertThat(auctionIds(recommendFacade.readPopularAuctionList()))
                .containsExactly(3002L, 3001L);
    }

    @Test
    @DisplayName("입찰이 하나도 없어도 실패하지 않고 마감이 가까운 순서로 돌려준다")
    void popular_withoutBids_ordersByEndTime() {
        auction(3001L, 301L, now.plusHours(72));
        auction(3002L, 302L, now.plusHours(2));
        auction(3003L, 303L, now.plusHours(1));
        auction(3004L, 304L, now.plusHours(120));
        auction(3005L, 305L, now.plusHours(73));

        assertThat(auctionIds(recommendFacade.readPopularAuctionList()))
                .containsExactly(3003L, 3002L, 3001L, 3005L, 3004L);
    }

    @Test
    @DisplayName("진행 중인 경매가 10개보다 많으면 10개만 돌려준다")
    void popular_returnsAtMostTen() {
        LongStream.rangeClosed(1, 12).forEach(i -> auction(3000L + i, 300L + i, now.plusHours(30 + i)));

        assertThat(auctionIds(recommendFacade.readPopularAuctionList()))
                .containsExactlyElementsOf(LongStream.rangeClosed(3001L, 3010L).boxed().toList());
    }

    private void auction(long auctionId, long productId, LocalDateTime endAt, long... bidderIds) {
        recommendKafkaListener.handle(productEvent(productId, CREATED, 0L));
        recommendKafkaListener.handle(new AuctionStartedEvent(auctionId, productId, START_PRICE, 50000L, now.minusDays(1), endAt, "ENGLISH"));
        for (long bidderId : bidderIds) {
            bidSequence++;
            recommendKafkaListener.handle(new AuctionBidSucceedEvent(
                    bidSequence, auctionId, productId, bidderId, START_PRICE + bidSequence * 1000, (int) bidSequence,
                    endAt, now.minusMinutes(bidSequence)));
        }
    }

    private void clean() {
        interactionLogRepository.deleteAll();
        interactionRepository.deleteAll();
        recommendAuctionRepository.deleteAll();
        recommendMemberRepository.deleteAll();
        productEmbeddingRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    private static ProductChangedEvent productEvent(long productId, ProductChangeType changeType, long version) {
        return new ProductChangedEvent(
                productId, changeType, version, SELLER_ID, "상품" + productId, CATEGORY, "SONY", null,
                START_PRICE, null, LocalDateTime.of(2026, 11, 12, 10, 0), null, LocalDateTime.of(2026, 11, 10, 9, 0)
        );
    }

    private static List<Long> auctionIds(List<AuctionResponseDto> result) {
        return result.stream().map(AuctionResponseDto::auctionId).toList();
    }
}
