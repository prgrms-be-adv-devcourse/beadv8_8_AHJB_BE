package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.auction.domain.Bid;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.market.auction.support.AuctionIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 입찰 내역 조회(#515): 경매별 최신순 Slice. 전체 개수 count 쿼리 없이 size+1건으로 다음 페이지 여부를 판단한다
class BidRepositoryTest extends AuctionIntegrationTestSupport {

    // 종료 스케줄러가 테스트 중에 경매를 마감하지 않도록 미래 시각으로 둔다
    private static final LocalDateTime START_AT = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);

    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;

    @AfterEach
    void tearDown() {
        bidRepository.deleteAllInBatch();
        auctionRepository.deleteAllInBatch();
    }

    private Long 경매(long productId) {
        return auctionRepository.save(EnglishAuction.create(productId, 100L, 10_000L, null, 3_000L, "CONSOLE",
                START_AT.plusDays(1), START_AT)).getId();
    }

    @Test
    void 이_경매의_입찰만_최신순으로_size만큼_읽고_다음_페이지_여부를_알려준다() {
        Long target = 경매(9_001L);
        Long other = 경매(9_002L);
        List<Long> targetBidIds = new ArrayList<>();
        // 두 경매의 입찰을 번갈아 저장해 id가 섞이게 한다
        for (int i = 0; i < 5; i++) {
            targetBidIds.add(bidRepository.save(new Bid(target, 200L + i, 10_000L + i * 1_000L, START_AT)).getId());
            bidRepository.save(new Bid(other, 300L + i, 10_000L + i * 1_000L, START_AT));
        }

        Slice<Bid> first = bidRepository.findByAuctionIdOrderByIdDesc(target, PageRequest.of(0, 2));
        Slice<Bid> last = bidRepository.findByAuctionIdOrderByIdDesc(target, PageRequest.of(2, 2));

        assertThat(first.getContent()).extracting(Bid::getId)
                .containsExactly(targetBidIds.get(4), targetBidIds.get(3));
        assertThat(first.hasNext()).isTrue();
        assertThat(last.getContent()).extracting(Bid::getId).containsExactly(targetBidIds.get(0));
        assertThat(last.hasNext()).isFalse();
        assertThat(first.getContent()).allMatch(bid -> bid.getAuctionId().equals(target));
    }

    @Test
    void 입찰이_없으면_빈_목록이고_다음_페이지가_없다() {
        Long id = 경매(9_003L);

        Slice<Bid> bids = bidRepository.findByAuctionIdOrderByIdDesc(id, PageRequest.of(0, 10));

        assertThat(bids.getContent()).isEmpty();
        assertThat(bids.hasNext()).isFalse();
    }
}
