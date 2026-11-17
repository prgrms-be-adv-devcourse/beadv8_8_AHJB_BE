package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.Auction;
import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.dto.DutchBuyResponseDto;
import com.jjinmak.auction.exception.AuctionErrorCode;
import com.jjinmak.auction.out.AuctionRepository;
import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.jjinmak.auction.domain.AuctionStatus.*;
import static com.jjinmak.auction.exception.AuctionErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuctionDutchBuyUseCaseTest {

    private static final Long AUCTION_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long SELLER_ID = 100L;     // 운영자 계정
    private static final Long BUYER_ID = 200L;
    private static final Long OTHER_ID = 300L;
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 9, 21, 0);
    private static final LocalDateTime NOW = START_AT.plusMinutes(5);          // 현재가 270,000원
    private static final LocalDateTime END_AT = START_AT.plusMinutes(26);      // 유찰 시각
    private static final String CATEGORY = "CONSOLE";

    @Mock AuctionRepository auctionRepository;
    @Mock AuctionEventPublisher auctionEventPublisher;

    AuctionDutchBuyUseCase auctionDutchBuyUseCase;

    // AuctionReader는 실제 객체로 조립해 findById mock을 그대로 쓴다
    @BeforeEach
    void setUp() {
        auctionDutchBuyUseCase = new AuctionDutchBuyUseCase(new AuctionReader(auctionRepository), auctionEventPublisher);
    }

    // ── 경매 만들기 ──
    // 시작가 30만, 최저가 15만, 2%, 60초 → 6,000원씩, 마지막 단계 25(150,000원), 21:26 유찰
    private DutchAuction 진행중_경매() {
        return DutchAuction.create(PRODUCT_ID, SELLER_ID, 300_000L, 150_000L, 2, 60, 3_000L, CATEGORY, START_AT);
    }

    private DutchAuction 판매된_경매() {
        DutchAuction auction = 진행중_경매();
        auction.buy(OTHER_ID, 300_000L, START_AT);
        return auction;
    }

    private DutchAuction 유찰된_경매() {
        DutchAuction auction = 진행중_경매();
        auction.close(END_AT);
        return auction;
    }

    private void 경매가_조회된다(Auction auction) {
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
    }

    private void 구매가_거부된다(Long memberId, Long expectedPrice, LocalDateTime now, AuctionErrorCode expected) {
        assertThatThrownBy(() -> auctionDutchBuyUseCase.buy(AUCTION_ID, memberId, expectedPrice, now))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
        then(auctionEventPublisher).shouldHaveNoInteractions();
    }

    @Nested
    class 구매_성공 {

        @Test
        void 현재가로_구매하면_낙찰되고_낙찰_이벤트를_발행한다() {
            DutchAuction auction = 진행중_경매();
            경매가_조회된다(auction);

            DutchBuyResponseDto response = auctionDutchBuyUseCase.buy(AUCTION_ID, BUYER_ID, 270_000L, NOW);

            assertThat(response.winningPrice()).isEqualTo(270_000L);
            assertThat(response.winAt()).isEqualTo(NOW);
            assertThat(response.status()).isEqualTo(WON);
            assertThat(auction.getWinnerId()).isEqualTo(BUYER_ID);
            then(auctionEventPublisher).should().publishWon(auction);
        }

        @Test
        void 본_가격보다_서버_가격이_낮으면_서버_가격으로_낙찰된다() {
            // 회원은 21:04에 276,000원을 보고 눌렀고 요청은 21:05에 처리됨
            경매가_조회된다(진행중_경매());

            DutchBuyResponseDto response = auctionDutchBuyUseCase.buy(AUCTION_ID, BUYER_ID, 276_000L, NOW);

            assertThat(response.winningPrice()).isEqualTo(270_000L);
        }

        @Test
        void 마지막_단계에서는_최저가로_낙찰된다() {
            경매가_조회된다(진행중_경매());

            DutchBuyResponseDto response = auctionDutchBuyUseCase.buy(
                    AUCTION_ID, BUYER_ID, 150_000L, END_AT.minusSeconds(1));

            assertThat(response.winningPrice()).isEqualTo(150_000L);
        }

        @Test
        void 최저가가_0원이면_0원으로_낙찰된다() {
            // 30만, 최저가 0, 2% → 50단계에 0원, 51분 뒤 유찰
            DutchAuction auction = DutchAuction.create(PRODUCT_ID, SELLER_ID, 300_000L, 0L, 2, 60, 3_000L, CATEGORY, START_AT);
            경매가_조회된다(auction);

            DutchBuyResponseDto response = auctionDutchBuyUseCase.buy(
                    AUCTION_ID, BUYER_ID, 0L, START_AT.plusMinutes(50));

            assertThat(response.winningPrice()).isZero();
            then(auctionEventPublisher).should().publishWon(auction);
        }
    }

    @Nested
    class 구매_거부 {

        @Test
        void 경매가_없으면_001() {
            given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.empty());

            구매가_거부된다(BUYER_ID, 270_000L, NOW, AUCTION_NOT_FOUND);
        }

        @Test
        void 영국식_경매는_012() {
            경매가_조회된다(EnglishAuction.create(PRODUCT_ID, SELLER_ID, 10_000L, null, 3_000L, CATEGORY,
                    START_AT.plusDays(1), START_AT));

            구매가_거부된다(BUYER_ID, 270_000L, NOW, AUCTION_TYPE_MISMATCH);
        }

        @Test
        void 이미_판매된_경매는_014() {
            경매가_조회된다(판매된_경매());

            구매가_거부된다(BUYER_ID, 270_000L, NOW, DUTCH_ALREADY_SOLD);
        }

        @Test
        void 판매_후_결제까지_끝난_경매도_014() {
            DutchAuction auction = 판매된_경매();
            auction.pay();
            경매가_조회된다(auction);

            구매가_거부된다(BUYER_ID, 270_000L, NOW, DUTCH_ALREADY_SOLD);
        }

        @Test
        void 유찰된_경매는_002() {
            경매가_조회된다(유찰된_경매());

            구매가_거부된다(BUYER_ID, 150_000L, END_AT, AUCTION_NOT_IN_PROGRESS);
        }

        @Test
        void 종료_시각이_지났지만_아직_유찰_처리_전이면_003() {
            경매가_조회된다(진행중_경매());

            구매가_거부된다(BUYER_ID, 150_000L, END_AT, AUCTION_ALREADY_ENDED);
        }

        @Test
        void 판매자_본인은_004() {
            경매가_조회된다(진행중_경매());

            구매가_거부된다(SELLER_ID, 270_000L, NOW, SELLER_CANNOT_BID);
        }

        @Test
        void 서버_가격이_본_가격보다_높으면_013() {
            // 클라이언트 시계가 빨라 다음 가격(264,000원)을 먼저 봄
            DutchAuction auction = 진행중_경매();
            경매가_조회된다(auction);

            구매가_거부된다(BUYER_ID, 264_000L, NOW, DUTCH_PRICE_CHANGED);
            assertThat(auction.getStatus()).isEqualTo(IN_PROGRESS);
        }
    }

    // 여러 조건이 동시에 걸릴 때 어떤 코드가 나가는지 (D-10: 001 → 012 → 014 → 002 → 003 → 004 → 013)
    @Nested
    class 검증_순서 {

        @Test
        void 판매됐으면_판매자가_요청해도_014가_먼저다() {
            경매가_조회된다(판매된_경매());

            구매가_거부된다(SELLER_ID, 270_000L, NOW, DUTCH_ALREADY_SOLD);
        }

        @Test
        void 판매됐으면_종료_시각이_지나도_014가_먼저다() {
            경매가_조회된다(판매된_경매());

            구매가_거부된다(BUYER_ID, 150_000L, END_AT.plusMinutes(1), DUTCH_ALREADY_SOLD);
        }

        @Test
        void 유찰됐으면_종료_시각_검사보다_002가_먼저다() {
            경매가_조회된다(유찰된_경매());

            구매가_거부된다(BUYER_ID, 150_000L, END_AT.plusMinutes(1), AUCTION_NOT_IN_PROGRESS);
        }

        @Test
        void 종료_시각이_지났으면_가격_검사보다_003이_먼저다() {
            경매가_조회된다(진행중_경매());

            구매가_거부된다(BUYER_ID, 0L, END_AT, AUCTION_ALREADY_ENDED);
        }

        @Test
        void 판매자면_가격_검사보다_004가_먼저다() {
            경매가_조회된다(진행중_경매());

            구매가_거부된다(SELLER_ID, 0L, NOW, SELLER_CANNOT_BID);
        }
    }
}
