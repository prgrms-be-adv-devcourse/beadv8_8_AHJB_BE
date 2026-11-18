package com.jjinmak.boundedcontext.market;

import com.jjinmak.boundedcontext.auction.domain.Auction;
import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.auction.domain.FailureReason;
import com.jjinmak.boundedcontext.auction.exception.AuctionErrorCode;
import com.jjinmak.boundedcontext.auction.out.AuctionRepository;
import com.jjinmak.boundedcontext.auction.support.AuctionIntegrationTestSupport;
import com.jjinmak.boundedcontext.market.app.MarketFacade;
import com.jjinmak.boundedcontext.market.in.dto.MarketRelistResponseDto;
import com.jjinmak.boundedcontext.product.product.domain.AuctionDuration;
import com.jjinmak.boundedcontext.product.product.domain.Product;
import com.jjinmak.boundedcontext.product.product.domain.ProductAuctionTerms;
import com.jjinmak.boundedcontext.product.product.domain.ProductMember;
import com.jjinmak.boundedcontext.product.product.domain.ShippingFeeType;
import com.jjinmak.boundedcontext.product.product.out.ProductMemberRepository;
import com.jjinmak.boundedcontext.product.product.out.ProductRepository;
import com.jjinmak.contracts.product.domain.Manufacturer;
import com.jjinmak.contracts.product.domain.ProductCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 경매 시작 → 유찰 → 재등록을 실제 PostgreSQL에서 한 트랜잭션 흐름으로 확인한다.
// (상품·경매 테이블, 회차 유니크 제약, 실패 시 롤백)
class MarketAuctionFlowIntegrationTest extends AuctionIntegrationTestSupport {

    // 실제로 도는 경매 시작·종료 스케줄러가 건드리지 않도록 하루 뒤를 기준 시각으로 쓴다
    private static final LocalDateTime CREATED_AT = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);
    private static final LocalDateTime OPEN_AT = CREATED_AT.plus(Product.EDITABLE_PERIOD);
    private static final LocalDateTime RELIST_AT = OPEN_AT.plusDays(2);
    private static final Long SELLER_ID = 100L;

    @Autowired MarketFacade marketFacade;
    @Autowired ProductRepository productRepository;
    @Autowired ProductMemberRepository productMemberRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        auctionRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        productMemberRepository.deleteAllInBatch();
    }

    private Long 상품을_등록한다() {
        ProductMember seller = productMemberRepository.save(new ProductMember(SELLER_ID, "판매자"));
        ProductAuctionTerms terms = ProductAuctionTerms.create(10_000L, null, AuctionDuration.DAYS_1, null, OPEN_AT);
        return productRepository.save(Product.create(seller, "스위치", ProductCategory.CONSOLE, Manufacturer.NINTENDO,
                null, "설명", ShippingFeeType.FREE, null, false, "v1", CREATED_AT, terms)).getId();
    }

    private void 유찰시킨다(Long auctionId) {
        transactionTemplate.executeWithoutResult(status ->
                auctionRepository.findById(auctionId).orElseThrow().fail(FailureReason.NO_BID));
    }

    @Test
    void 경매를_열고_유찰되면_다음_회차로_다시_연다() {
        Long productId = 상품을_등록한다();

        marketFacade.openAuction(productId, OPEN_AT);

        Product opened = productRepository.findById(productId).orElseThrow();
        Long firstAuctionId = opened.getCurrentAuctionId();
        assertThat(firstAuctionId).isNotNull();
        assertThat(auctionRepository.findById(firstAuctionId).orElseThrow().getStatus()).isEqualTo(AuctionStatus.IN_PROGRESS);

        유찰시킨다(firstAuctionId);
        MarketRelistResponseDto response = marketFacade.relist(SELLER_ID, productId, RELIST_AT);

        assertThat(response.round()).isEqualTo(2);
        Product relisted = productRepository.findById(productId).orElseThrow();
        assertThat(relisted.getRound()).isEqualTo(2);
        assertThat(relisted.getCurrentAuctionId()).isEqualTo(response.auctionId());

        List<Auction> auctions = auctionRepository.findAll();
        assertThat(auctions).extracting(Auction::getRound).containsExactlyInAnyOrder(1, 2);
        Auction second = auctionRepository.findById(response.auctionId()).orElseThrow();
        assertThat(second.getStatus()).isEqualTo(AuctionStatus.IN_PROGRESS);
        assertThat(second.getStartAt()).isEqualTo(RELIST_AT);
        assertThat(second.getEndAt()).isEqualTo(RELIST_AT.plusDays(1));   // 이전 회차와 같은 기간
    }

    @Test
    void 진행_중인_경매는_재등록할_수_없고_상품도_그대로다() {
        Long productId = 상품을_등록한다();
        marketFacade.openAuction(productId, OPEN_AT);
        Long firstAuctionId = productRepository.findById(productId).orElseThrow().getCurrentAuctionId();

        assertThatThrownBy(() -> marketFacade.relist(SELLER_ID, productId, RELIST_AT))
                .extracting("errorCode").isEqualTo(AuctionErrorCode.AUCTION_NOT_RELISTABLE);

        // 상품의 회차 변경까지 롤백된다
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getRound()).isEqualTo(1);
        assertThat(product.getCurrentAuctionId()).isEqualTo(firstAuctionId);
        assertThat(auctionRepository.count()).isEqualTo(1);
    }
}
