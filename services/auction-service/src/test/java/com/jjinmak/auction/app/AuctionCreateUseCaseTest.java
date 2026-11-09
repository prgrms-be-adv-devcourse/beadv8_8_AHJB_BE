package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.Auction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.dto.AuctionCreateDto;
import com.jjinmak.auction.out.AuctionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuctionCreateUseCaseTest {

    private static final Long PRODUCT_ID = 1L;

    @Mock AuctionRepository auctionRepository;
    @Mock AuctionEventPublisher auctionEventPublisher;

    @InjectMocks AuctionCreateUseCase auctionCreateUseCase;

    private AuctionCreateDto 생성_요청() {
        return new AuctionCreateDto(
                PRODUCT_ID, 100L, 10_000L, null, 3_000L,
                LocalDateTime.now().plusDays(3),
                "테스트 상품", "CONSOLE", null
        );
    }

    @Test
    void 처음_받은_요청이면_경매를_생성하고_시작_이벤트를_발행한다() {
        given(auctionRepository.existsByProductId(PRODUCT_ID)).willReturn(false);
        given(auctionRepository.save(any(Auction.class))).willAnswer(inv -> inv.getArgument(0));

        auctionCreateUseCase.createAuction(생성_요청());

        then(auctionRepository).should().save(any(Auction.class));
        then(auctionEventPublisher).should().publishStarted(any(EnglishAuction.class));
    }

    @Test
    void 같은_상품의_경매가_이미_있으면_상태와_무관하게_생성하지_않는다() {
        given(auctionRepository.existsByProductId(PRODUCT_ID)).willReturn(true);

        auctionCreateUseCase.createAuction(생성_요청());

        then(auctionRepository).should(never()).save(any());
        then(auctionEventPublisher).should(never()).publishStarted(any());
    }
}