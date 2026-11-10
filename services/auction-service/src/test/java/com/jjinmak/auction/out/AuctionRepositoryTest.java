package com.jjinmak.auction.out;

import com.jjinmak.auction.domain.Auction;
import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.support.AuctionIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// SINGLE_TABLE 상속이 실제 PostgreSQL에서 동작하는지 확인한다.
// (구분 컬럼 CHECK 제약, 하위 클래스 컬럼 nullable, 유형별 JPQL)
class AuctionRepositoryTest extends AuctionIntegrationTestSupport {

    // 종료 스케줄러(1초 주기)가 테스트 중에 경매를 마감하지 않도록 미래 시각으로 둔다
    private static final LocalDateTime START_AT = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);
    private static final String CATEGORY = "CONSOLE";
    private static final List<AuctionStatus> IN_PROGRESS = List.of(AuctionStatus.IN_PROGRESS);

    @Autowired AuctionRepository auctionRepository;

    @AfterEach
    void tearDown() {
        auctionRepository.deleteAllInBatch();
    }

    private EnglishAuction 영국식() {
        return EnglishAuction.create(1L, 100L, 10_000L, 50_000L, 3_000L, CATEGORY, START_AT.plusDays(1), START_AT);
    }

    private DutchAuction 하향식() {
        return DutchAuction.create(2L, 100L, 300_000L, 150_000L, 2, 60, 3_000L, CATEGORY, START_AT);
    }

    @Test
    void 두_유형을_한_테이블에_저장하고_유형대로_다시_읽는다() {
        Long englishId = auctionRepository.save(영국식()).getId();
        Long dutchId = auctionRepository.save(하향식()).getId();

        Auction english = auctionRepository.findById(englishId).orElseThrow();
        Auction dutch = auctionRepository.findById(dutchId).orElseThrow();

        assertThat(english).isInstanceOf(EnglishAuction.class);
        assertThat(dutch).isInstanceOf(DutchAuction.class);
    }

    @Test
    void 하향식_조건과_계산된_종료_시각이_그대로_저장된다() {
        Long id = auctionRepository.save(하향식()).getId();

        DutchAuction found = (DutchAuction) auctionRepository.findById(id).orElseThrow();

        assertThat(found.getFloorPrice()).isEqualTo(150_000L);
        assertThat(found.getDropRatePercent()).isEqualTo(2);
        assertThat(found.getDropIntervalSec()).isEqualTo(60);
        assertThat(found.getEndAt()).isEqualTo(START_AT.plusMinutes(26));
        assertThat(found.currentPriceAt(START_AT.plusMinutes(5))).isEqualTo(270_000L);
    }

    @Test
    void 유형별_목록은_자기_유형만_조회한다() {
        auctionRepository.save(영국식());
        auctionRepository.save(하향식());

        assertThat(auctionRepository.findEnglishList(IN_PROGRESS, null, PageRequest.of(0, 10)).getContent())
                .hasSize(1)
                .allMatch(EnglishAuction.class::isInstance);
        assertThat(auctionRepository.findDutchList(IN_PROGRESS, PageRequest.of(0, 10)).getContent())
                .hasSize(1)
                .allMatch(DutchAuction.class::isInstance);
    }
}
