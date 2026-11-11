package com.jjinmak.auction.out;

import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.domain.Bid;
import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.support.AuctionIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 마감 임박 대상 조회와 1회 표시(벌크 UPDATE)를 실제 PostgreSQL에서 확인한다.
class AuctionEndingSoonQueryTest extends AuctionIntegrationTestSupport {

    // 실제로 도는 종료·마감 임박 스케줄러가 건드리지 않도록 하루 뒤를 기준 시각으로 쓴다
    private static final LocalDateTime NOW = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);
    private static final LocalDateTime THRESHOLD = NOW.plusMinutes(10);
    private static final AuctionStatus IN_PROGRESS = AuctionStatus.IN_PROGRESS;

    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        bidRepository.deleteAllInBatch();
        auctionRepository.deleteAllInBatch();
    }

    private EnglishAuction 영국식(LocalDateTime endAt, boolean hasBid) {
        EnglishAuction auction = EnglishAuction.create(1L, 100L, 10_000L, null, 3_000L, "CONSOLE", endAt, NOW.minusDays(3));
        if (hasBid) {
            auction.updateHighestBid(200L, 10_000L);
        }
        return auctionRepository.save(auction);
    }

    private int 표시한다(Long auctionId) {
        return transactionTemplate.execute(status -> auctionRepository.markEndingSoonNotified(auctionId, IN_PROGRESS, NOW));
    }

    private List<Long> 대상() {
        return auctionRepository.findEndingSoonTargetIds(IN_PROGRESS, NOW, THRESHOLD, PageRequest.of(0, 100));
    }

    @Test
    void 입찰이_있고_10분_안에_끝나는_진행중_영국식_경매만_대상이다() {
        Long target = 영국식(NOW.plusMinutes(5), true).getId();
        영국식(NOW.plusMinutes(5), false);                 // 입찰 없음
        영국식(NOW.plusMinutes(30), true);                 // 아직 멀었음
        영국식(NOW.minusMinutes(1), true);                 // 이미 종료 시각 지남
        EnglishAuction won = 영국식(NOW.plusMinutes(5), true);
        won.close(NOW);                                    // 낙찰로 끝남
        auctionRepository.save(won);
        auctionRepository.save(DutchAuction.create(2L, 100L, 300_000L, 150_000L, 2, 60, 3_000L, "CONSOLE",
                NOW.minusMinutes(20)));                    // 하향식(종료 NOW+6분)

        assertThat(대상()).containsExactly(target);
    }

    @Test
    void 한_번_표시한_경매는_다시_표시되지_않고_대상에서도_빠진다() {
        Long id = 영국식(NOW.plusMinutes(5), true).getId();

        assertThat(표시한다(id)).isEqualTo(1);
        assertThat(표시한다(id)).isZero();
        assertThat(대상()).isEmpty();
    }

    @Test
    void 표시해도_경매_버전이_올라가지_않아_동시_입찰과_충돌하지_않는다() {
        EnglishAuction saved = 영국식(NOW.plusMinutes(5), true);
        Long versionBefore = auctionRepository.findById(saved.getId()).orElseThrow().getVersion();

        표시한다(saved.getId());

        assertThat(auctionRepository.findById(saved.getId()).orElseThrow().getVersion()).isEqualTo(versionBefore);
    }

    @Test
    void 진행중이_아닌_경매는_표시되지_않는다() {
        EnglishAuction won = 영국식(NOW.plusMinutes(5), true);
        won.close(NOW);
        auctionRepository.save(won);

        assertThat(표시한다(won.getId())).isZero();
    }

    @Test
    void 입찰자_목록은_같은_회원이_여러_번_입찰해도_한_번만_나온다() {
        Long id = 영국식(NOW.plusMinutes(5), true).getId();
        bidRepository.save(new Bid(id, 200L, 10_000L, NOW.minusHours(2)));
        bidRepository.save(new Bid(id, 300L, 11_000L, NOW.minusHours(1)));
        bidRepository.save(new Bid(id, 200L, 12_000L, NOW.minusMinutes(30)));

        assertThat(bidRepository.findDistinctBidderIds(id)).containsExactlyInAnyOrder(200L, 300L);
    }
}
