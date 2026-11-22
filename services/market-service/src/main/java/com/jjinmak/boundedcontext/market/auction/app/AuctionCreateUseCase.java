package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.auction.domain.Auction;
import com.jjinmak.boundedcontext.market.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionCreateDto;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionCreateResultDto;
import com.jjinmak.boundedcontext.market.auction.out.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCreateUseCase {
    private final AuctionRepository auctionRepository;
    private final AuctionEventPublisher auctionEventPublisher;

    // 같은 상품·같은 회차의 경매가 이미 있으면 새로 만들지 않고 그 경매를 돌려준다. (같은 요청이 두 번 와도 결과가 같다)
    // 동시에 두 번 생성되는 경우는 (product_id, round) 유니크 제약이 막는다.
    public AuctionCreateResultDto createAuction(AuctionCreateDto dto){
        Optional<Auction> existing = auctionRepository.findByProductIdAndRound(dto.productId(), dto.round());
        if (existing.isPresent()) {
            log.info("경매 생성 중복 요청, 기존 경매 반환: productId={}, round={}, auctionId={}",
                    dto.productId(), dto.round(), existing.get().getId());
            return AuctionCreateResultDto.from(existing.get());
        }
        Auction saved = auctionRepository.save(newAuction(dto));
        auctionEventPublisher.publishStarted(saved);
        return AuctionCreateResultDto.from(saved);
    }

    // 일괄 열기 (#548): 중복 확인을 한 번의 조회로 하고, 요청 순서대로 결과를 돌려준다.
    // 이미 있는 회차는 그 경매를 돌려준다(건별과 같은 규칙). 이벤트는 아웃박스에 모였다가 커밋 직전 한 번에 저장된다.
    public List<AuctionCreateResultDto> createAuctions(List<AuctionCreateDto> dtos){
        Map<String, Auction> existing = auctionRepository.findByProductIdIn(
                        dtos.stream().map(AuctionCreateDto::productId).toList()).stream()
                .collect(Collectors.toMap(a -> key(a.getProductId(), a.getRound()), Function.identity()));

        List<AuctionCreateResultDto> results = new ArrayList<>(dtos.size());
        for (AuctionCreateDto dto : dtos) {
            Auction found = existing.get(key(dto.productId(), dto.round()));
            if (found != null) {
                log.info("경매 생성 중복 요청, 기존 경매 반환: productId={}, round={}, auctionId={}",
                        dto.productId(), dto.round(), found.getId());
                results.add(AuctionCreateResultDto.from(found));
                continue;
            }
            Auction saved = auctionRepository.save(newAuction(dto));
            auctionEventPublisher.publishStarted(saved);
            results.add(AuctionCreateResultDto.from(saved));
        }
        return results;
    }

    private Auction newAuction(AuctionCreateDto dto) {
        Auction auction = switch (dto.auctionType()) {
            case ENGLISH -> EnglishAuction.create(dto.productId(), dto.sellerId(), dto.startPrice(),
                    dto.instantWinPrice(), dto.shippingFee(), dto.category(), dto.endAt(), dto.startAt(),
                    dto.round());
            case DUTCH -> DutchAuction.create(dto.productId(), dto.sellerId(), dto.startPrice(),
                    dto.floorPrice(), dto.dropRatePercent(), dto.dropIntervalSec(),
                    dto.shippingFee(), dto.category(), dto.startAt(), dto.round());
        };
        if (auction.isEnded(LocalDateTime.now())) {
            log.warn("경매 생성 시점에 이미 종료 시각이 지남: productId={}, type={}, endAt={}",
                    dto.productId(), dto.auctionType(), auction.getEndAt());
        }
        return auction;
    }

    private static String key(Long productId, int round) {
        return productId + ":" + round;
    }
}
