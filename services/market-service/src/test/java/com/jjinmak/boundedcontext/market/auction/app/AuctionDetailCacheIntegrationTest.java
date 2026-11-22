package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.market.auction.dto.EnglishAuctionDetailResponseDto;
import com.jjinmak.boundedcontext.market.auction.out.AuctionRepository;
import com.jjinmak.boundedcontext.market.auction.support.AuctionIntegrationTestSupport;
import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 상세 캐시를 켜고 실제 PostgreSQL·트랜잭션에서 확인한다 (#557).
// TTL은 길게(60초) 둬서 "이벤트로 지웠는지"만 본다. TTL 만료는 AuctionDetailCacheTest에서 검증
@TestPropertySource(properties = {
        "market.auction.detail-cache.enabled=true",
        "market.auction.detail-cache.ttl-ms=60000"
})
class AuctionDetailCacheIntegrationTest extends AuctionIntegrationTestSupport {

    private static final Long SELLER_ID = 100L;
    private static final Long BIDDER_ID = 200L;
    private static final Long OTHER_ID = 300L;

    @Autowired AuctionFacade auctionFacade;
    @Autowired AuctionRepository auctionRepository;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long id : created) {
            jdbcTemplate.update("delete from bid where auction_id = ?", id);
            jdbcTemplate.update("delete from auction where id = ?", id);
        }
    }

    private Long 진행중_경매() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        Long id = auctionRepository.save(EnglishAuction.create(557_000L + created.size(), SELLER_ID, 10_000L, 50_000L,
                3_000L, "CONSOLE", now.plusDays(1), now.minusHours(1))).getId();
        created.add(id);
        return id;
    }

    private EnglishAuctionDetailResponseDto 상세(Long auctionId, Long memberId) {
        return (EnglishAuctionDetailResponseDto) auctionFacade.detail(auctionId, memberId);
    }

    // 이벤트 없이 DB만 바꾼다 (캐시가 쓰이는지 보려고)
    private void DB에서_시작가를_바꾼다(Long auctionId, long startPrice) {
        jdbcTemplate.update("update auction set start_price = ? where id = ?", startPrice, auctionId);
    }

    @Test
    void 이벤트_없이_DB만_바뀌면_캐시된_값이_나간다() {
        Long id = 진행중_경매();
        상세(id, null);

        DB에서_시작가를_바꾼다(id, 99_000L);

        assertThat(상세(id, null).auction().startPrice()).isEqualTo(10_000L);
    }

    @Test
    void 입찰이_커밋되면_다음_조회에_새_가격이_나온다() {
        Long id = 진행중_경매();
        assertThat(상세(id, null).auction().bidCount()).isZero();

        auctionFacade.bid(id, BIDDER_ID, 20_000L);

        EnglishAuctionDetailResponseDto after = 상세(id, null);
        assertThat(after.auction().currentPrice()).isEqualTo(20_000L);
        assertThat(after.auction().bidCount()).isEqualTo(1);
    }

    @Test
    void 실패한_입찰은_캐시를_지우지_않는다() {
        Long id = 진행중_경매();
        상세(id, null);
        DB에서_시작가를_바꾼다(id, 99_000L);

        // 판매자 본인 입찰 → 업무 예외로 롤백
        assertThatThrownBy(() -> auctionFacade.bid(id, SELLER_ID, 20_000L)).isInstanceOf(BusinessException.class);

        assertThat(상세(id, null).auction().startPrice()).isEqualTo(10_000L);
    }

    @Test
    void 같은_캐시_값이어도_회원마다_자기_me를_받는다() {
        Long id = 진행중_경매();
        auctionFacade.bid(id, BIDDER_ID, 20_000L);
        상세(id, null);   // 캐시에 넣기

        assertThat(상세(id, SELLER_ID).me().isSeller()).isTrue();
        assertThat(상세(id, BIDDER_ID).me().isHighestBidder()).isTrue();
        assertThat(상세(id, OTHER_ID).me().isSeller()).isFalse();
        assertThat(상세(id, OTHER_ID).me().isHighestBidder()).isFalse();
        assertThat(상세(id, null).me().isHighestBidder()).isFalse();
    }

    @Test
    void 일괄_유찰이_커밋되면_종료_상태가_나온다() {
        Long id = 진행중_경매();
        jdbcTemplate.update("update auction set end_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), id);
        assertThat(상세(id, null).auction().status()).isEqualTo(AuctionStatus.IN_PROGRESS);

        auctionFacade.failNoBidAuctions(LocalDateTime.now(), 1_000);

        assertThat(상세(id, null).auction().status()).isEqualTo(AuctionStatus.FAILED);
    }
}
