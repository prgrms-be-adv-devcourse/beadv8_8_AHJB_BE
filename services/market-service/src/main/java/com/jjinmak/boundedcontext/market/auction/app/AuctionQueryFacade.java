package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.auction.dto.AuctionStateDto;
import com.jjinmak.boundedcontext.market.auction.out.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 다른 컨텍스트(상품)가 경매를 읽기만 할 때 쓰는 입구.
// AuctionFacade는 경매 목록에 상품 정보를 붙이려고 상품을 읽으므로, 상품이 AuctionFacade를 읽으면 빈 순환이 생긴다.
// 그래서 상품 쪽에 의존하지 않는 조회 전용 Facade를 따로 둔다.
@Service
@RequiredArgsConstructor
public class AuctionQueryFacade {

    private final AuctionRepository auctionRepository;

    // 경매 id → 상태. 없는 id는 결과에 들어가지 않는다.
    @Transactional(readOnly = true)
    public Map<Long, AuctionStateDto> getAuctionStates(Collection<Long> auctionIds) {
        if (auctionIds.isEmpty()) {
            return Map.of();
        }
        return auctionRepository.findAllById(auctionIds).stream()
                .map(AuctionStateDto::from)
                .collect(Collectors.toMap(AuctionStateDto::auctionId, Function.identity()));
    }
}
