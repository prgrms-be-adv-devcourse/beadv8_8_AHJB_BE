package com.jjinmak.auction.in;

import com.jjinmak.auction.app.AuctionFacade;
import com.jjinmak.auction.domain.AuctionType;
import com.jjinmak.auction.dto.AuctionCreateDto;
import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent.DutchTerms;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent.EnglishTerms;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class AuctionEventListenerTest {

    private static final LocalDateTime SCHEDULED_AT = LocalDateTime.of(2026, 11, 20, 21, 0);

    @Mock AuctionFacade auctionFacade;

    AuctionEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuctionEventListener(auctionFacade);
    }

    private ProductRegisteredEvent 옛_형태(int duration, LocalDateTime customEndAt) {
        return new ProductRegisteredEvent(1L, 100L, 3_000L, 10_000L, 50_000L, duration, customEndAt,
                "상품", "CONSOLE", null);
    }

    private ProductRegisteredEvent 새_형태(String type, LocalDateTime scheduledAt,
                                         EnglishTerms english, DutchTerms dutch) {
        return new ProductRegisteredEvent(1L, 100L, 3_000L, 300_000L, null, 0, null,
                null, "CONSOLE", null, type, scheduledAt, english, dutch);
    }

    private AuctionCreateDto 생성_요청을_꺼낸다() {
        ArgumentCaptor<AuctionCreateDto> captor = ArgumentCaptor.forClass(AuctionCreateDto.class);
        then(auctionFacade).should().createAuction(captor.capture());
        return captor.getValue();
    }

    private void DLT로_보낸다(ProductRegisteredEvent event) {
        assertThatThrownBy(() -> listener.handleProductRegistered(event))
                .isInstanceOf(InvalidEventException.class);
    }

    @Nested
    class 옛_형태 {

        @Test
        void 유형이_없으면_영국식으로_받은_시각부터_기간만큼_연다() {
            LocalDateTime before = LocalDateTime.now();

            listener.handleProductRegistered(옛_형태(3, null));

            AuctionCreateDto dto = 생성_요청을_꺼낸다();
            assertThat(dto.auctionType()).isEqualTo(AuctionType.ENGLISH);
            assertThat(dto.startAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(LocalDateTime.now());
            assertThat(dto.endAt()).isEqualTo(dto.startAt().plusDays(3));
            assertThat(dto.instantWinPrice()).isEqualTo(50_000L);
        }

        @Test
        void 직접_지정한_종료_시각이_있으면_그대로_쓴다() {
            LocalDateTime customEndAt = LocalDateTime.of(2026, 11, 25, 22, 0);

            listener.handleProductRegistered(옛_형태(0, customEndAt));

            assertThat(생성_요청을_꺼낸다().endAt()).isEqualTo(customEndAt);
        }
    }

    @Nested
    class 새_형태 {

        @Test
        void 영국식은_공지_시각에_시작하고_상품이_보낸_종료_시각을_쓴다() {
            LocalDateTime endAt = SCHEDULED_AT.plusDays(3);

            listener.handleProductRegistered(
                    새_형태("ENGLISH", SCHEDULED_AT, new EnglishTerms(80_000L, endAt), null));

            AuctionCreateDto dto = 생성_요청을_꺼낸다();
            assertThat(dto.auctionType()).isEqualTo(AuctionType.ENGLISH);
            assertThat(dto.startAt()).isEqualTo(SCHEDULED_AT);
            assertThat(dto.endAt()).isEqualTo(endAt);
            assertThat(dto.instantWinPrice()).isEqualTo(80_000L);
        }

        @Test
        void 네덜란드식은_공지_시각에_시작하고_조건을_넘긴다() {
            listener.handleProductRegistered(
                    새_형태("DUTCH", SCHEDULED_AT, null, new DutchTerms(150_000L, 2, 60)));

            AuctionCreateDto dto = 생성_요청을_꺼낸다();
            assertThat(dto.auctionType()).isEqualTo(AuctionType.DUTCH);
            assertThat(dto.startAt()).isEqualTo(SCHEDULED_AT);
            assertThat(dto.floorPrice()).isEqualTo(150_000L);
            assertThat(dto.dropRatePercent()).isEqualTo(2);
            assertThat(dto.dropIntervalSec()).isEqualTo(60);
            assertThat(dto.endAt()).isNull();
        }
    }

    @Nested
    class 잘못된_이벤트는_재시도_없이_DLT {

        @Test
        void 알_수_없는_유형() {
            DLT로_보낸다(새_형태("REVERSE", SCHEDULED_AT, null, null));
            then(auctionFacade).shouldHaveNoInteractions();
        }

        @Test
        void 네덜란드식인데_조건이_없음() {
            DLT로_보낸다(새_형태("DUTCH", SCHEDULED_AT, null, null));
            then(auctionFacade).shouldHaveNoInteractions();
        }

        @Test
        void 네덜란드식인데_공지_시각이_없음() {
            DLT로_보낸다(새_형태("DUTCH", null, null, new DutchTerms(150_000L, 2, 60)));
            then(auctionFacade).shouldHaveNoInteractions();
        }

        @Test
        void 가격_조건이_정책에_맞지_않음() {
            willThrow(new IllegalArgumentException("최저가가 시작가 이상"))
                    .given(auctionFacade).createAuction(any());

            DLT로_보낸다(새_형태("DUTCH", SCHEDULED_AT, null, new DutchTerms(300_000L, 2, 60)));
        }
    }
}
