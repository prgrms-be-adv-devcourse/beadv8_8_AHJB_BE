package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.auction.domain.Auction;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.market.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.market.auction.domain.FailureReason;
import com.jjinmak.boundedcontext.market.auction.out.AuctionBulkCloseRepository.FailedAuction;
import com.jjinmak.boundedcontext.market.auction.support.AuctionIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 입찰 없는 경매 일괄 유찰 (#516). 실제 PostgreSQL에서 UPDATE ... RETURNING · FOR UPDATE SKIP LOCKED를 확인한다.
// 종료 스케줄러(1초 주기, 실제 시각)가 끼어들지 않도록 경매는 먼 미래에 끝나게 만들고, 호출할 때 now를 그보다 뒤로 준다.
class AuctionBulkCloseRepositoryTest extends AuctionIntegrationTestSupport {

    private static final LocalDateTime START_AT = LocalDateTime.now().plusDays(10).truncatedTo(ChronoUnit.SECONDS);
    private static final LocalDateTime END_AT = START_AT.plusDays(1);
    private static final LocalDateTime NOW = START_AT.plusDays(30);   // 모든 시드 경매의 종료 시각 이후
    private static final Long SELLER_ID = 100L;

    @Autowired AuctionRepository auctionRepository;
    @Autowired AuctionBulkCloseRepository auctionBulkCloseRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        auctionRepository.deleteAllInBatch();
    }

    private EnglishAuction 영국식(long productId, LocalDateTime endAt) {
        return EnglishAuction.create(productId, SELLER_ID, 10_000L, null, 3_000L, "CONSOLE", endAt, START_AT);
    }

    private List<FailedAuction> 일괄_유찰(int limit) {
        return transactionTemplate.execute(status ->
                auctionBulkCloseRepository.failNoBidAuctions(NOW, NOW, limit));
    }

    @Test
    void 종료된_진행중_경매_중_입찰_없는_영국식과_하향식만_유찰한다() {
        EnglishAuction noBid = auctionRepository.save(영국식(8_001L, END_AT));
        EnglishAuction withBid = 영국식(8_002L, END_AT);
        withBid.updateHighestBid(200L, 10_000L);
        withBid = auctionRepository.save(withBid);
        EnglishAuction notEnded = auctionRepository.save(영국식(8_003L, NOW.plusDays(1)));
        DutchAuction dutch = auctionRepository.save(
                DutchAuction.create(8_004L, SELLER_ID, 300_000L, 150_000L, 2, 60, 3_000L, "CONSOLE", START_AT));
        Long noBidVersion = noBid.getVersion();

        List<FailedAuction> failed = 일괄_유찰(1000);

        assertThat(failed).extracting(FailedAuction::auctionId)
                .containsExactlyInAnyOrder(noBid.getId(), dutch.getId());
        assertThat(failed).allMatch(f -> f.sellerId().equals(SELLER_ID));
        assertThat(failed).filteredOn(f -> f.auctionId().equals(noBid.getId()))
                .extracting(FailedAuction::productId).containsExactly(8_001L);

        Auction failedNoBid = auctionRepository.findById(noBid.getId()).orElseThrow();
        assertThat(failedNoBid.getStatus()).isEqualTo(AuctionStatus.FAILED);
        assertThat(failedNoBid.getFailureReason()).isEqualTo(FailureReason.NO_BID);
        assertThat(failedNoBid.getVersion()).isEqualTo(noBidVersion + 1);   // 먼저 읽은 입찰은 낙관적 락 충돌
        assertThat(failedNoBid.getUpdatedAt()).isEqualTo(NOW);

        assertThat(auctionRepository.findById(withBid.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.IN_PROGRESS);   // 낙찰은 건별 처리 몫
        assertThat(auctionRepository.findById(notEnded.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.IN_PROGRESS);
    }

    @Test
    void 한_번에_limit건까지_종료_시각이_이른_순으로_처리한다() {
        EnglishAuction first = auctionRepository.save(영국식(8_011L, END_AT));
        EnglishAuction second = auctionRepository.save(영국식(8_012L, END_AT.plusMinutes(1)));
        EnglishAuction third = auctionRepository.save(영국식(8_013L, END_AT.plusMinutes(2)));

        List<FailedAuction> failed = 일괄_유찰(2);

        assertThat(failed).extracting(FailedAuction::auctionId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(auctionRepository.findById(third.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.IN_PROGRESS);
    }

    @Test
    void 이미_유찰된_경매는_다시_처리하지_않는다() {
        auctionRepository.save(영국식(8_021L, END_AT));

        assertThat(일괄_유찰(1000)).hasSize(1);
        assertThat(일괄_유찰(1000)).isEmpty();
    }
}
