package com.jjinmak.auction.dto;

import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.domain.ProductSnapshot;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 하향식은 최저가·종료 시각이 비공개다 (R4, R5, D-12).
// DTO에 필드가 없다는 것을 JSON으로 고정해, 나중에 필드를 추가해도 바로 잡히게 한다.
class DutchAuctionResponseJsonTest {

    private static final Long BUYER_ID = 200L;
    private static final Long SELLER_ID = 100L;
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 9, 21, 0);
    private static final LocalDateTime MIDDLE = START_AT.plusMinutes(5).plusSeconds(30);       // 270,000원
    private static final LocalDateTime LAST_STEP = START_AT.plusMinutes(25).plusSeconds(30);   // 마지막 단계
    private static final ProductSnapshot SNAPSHOT = ProductSnapshot.of("테스트 상품", "CONSOLE", null);

    private final JsonMapper mapper = JsonMapper.builder().build();

    // 시작가 30만, 최저가 15만, 2%, 60초 → 6,000원씩, 마지막 단계 25(150,000원), 21:26 유찰
    private DutchAuction 하향식_경매() {
        return DutchAuction.create(10L, SELLER_ID, 300_000L, 150_000L, 2, 60, 3_000L, SNAPSHOT, START_AT);
    }

    private String json(Object value) {
        return mapper.writeValueAsString(value);
    }

    private void 비공개_항목이_없다(String json) {
        assertThat(json)
                .doesNotContain("\"floorPrice\"")
                .doesNotContain("\"endAt\"")
                .doesNotContain("\"dropRatePercent\"")
                .doesNotContain("T21:26");          // 종료 시각 값 자체도 다른 필드로 새지 않는다
    }

    @Nested
    class 상세 {

        @Test
        void 진행_중에는_최저가와_종료_시각이_키도_값도_없다() {
            String json = json(DutchAuctionDetailResponseDto.of(하향식_경매(), BUYER_ID, MIDDLE));

            비공개_항목이_없다(json);
            assertThat(json).doesNotContain("150000");   // 최저가 값
            assertThat(json)
                    .contains("\"auctionType\":\"DUTCH\"")
                    .contains("\"currentPrice\":270000")
                    .contains("\"nextPrice\":264000")
                    .contains("\"isLastPrice\":false")
                    .contains("\"isSeller\":false");
        }

        @Test
        void 마지막_단계에는_다음_가격과_다음_하락_시각이_null이고_마지막임을_알린다() {
            String json = json(DutchAuctionDetailResponseDto.of(하향식_경매(), BUYER_ID, LAST_STEP));

            비공개_항목이_없다(json);
            assertThat(json)
                    .contains("\"nextPrice\":null")
                    .contains("\"nextDropAt\":null")
                    .contains("\"isLastPrice\":true");
        }

        @Test
        void 판매된_뒤에도_종료_시각은_없다() {
            DutchAuction auction = 하향식_경매();
            auction.buy(BUYER_ID, 270_000L, MIDDLE);

            String json = json(DutchAuctionDetailResponseDto.of(auction, BUYER_ID, MIDDLE.plusMinutes(1)));

            비공개_항목이_없다(json);
            assertThat(json)
                    .contains("\"winningPrice\":270000")
                    .contains("\"nextPrice\":null")
                    .contains("\"isLastPrice\":false");
        }

        @Test
        void 영국식_상세에는_종료_시각이_그대로_있다() {
            // 하향식 숨김 처리가 영국식 응답을 건드리지 않았는지 대조
            EnglishAuction english = EnglishAuction.create(10L, SELLER_ID, 10_000L, null, 3_000L, SNAPSHOT,
                    START_AT.plusDays(1), START_AT);

            String json = json(EnglishAuctionDetailResponseDto.of(english, BUYER_ID, MIDDLE));

            assertThat(json)
                    .contains("\"auctionType\":\"ENGLISH\"")
                    .contains("\"endAt\"");
        }
    }

    @Nested
    class 목록 {

        @Test
        void 목록_카드에도_최저가와_종료_시각이_없다() {
            String json = json(DutchAuctionListItemDto.from(하향식_경매(), MIDDLE));

            비공개_항목이_없다(json);
            assertThat(json).doesNotContain("150000");
            assertThat(json)
                    .contains("\"currentPrice\":270000")
                    .contains("\"dropAmount\":6000")
                    .contains("\"isLastPrice\":false");
        }

        @Test
        void 마지막_단계_목록_카드에도_종료_시각이_없다() {
            String json = json(DutchAuctionListItemDto.from(하향식_경매(), LAST_STEP));

            비공개_항목이_없다(json);
            assertThat(json)
                    .contains("\"nextDropAt\":null")
                    .contains("\"isLastPrice\":true");
        }
    }
}
