package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.shared.domain.MarketMember;
import com.jjinmak.boundedcontext.market.shared.out.MarketMemberRepository;
import com.jjinmak.boundedcontext.market.auction.domain.Auction;
import com.jjinmak.boundedcontext.market.auction.domain.Bid;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionBidHistoryItemDto;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionBidHistoryResponseDto;
import com.jjinmak.boundedcontext.market.auction.out.BidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuctionBidHistoryUseCase {
    private static final int MAX_SIZE = 50;

    private final AuctionReader auctionReader;
    private final BidRepository bidRepository;
    private final MarketMemberRepository marketMemberRepository;

    public AuctionBidHistoryResponseDto bidHistory(Long auctionId,Long memberId,int page,int size){
        // 경매를 한 번 읽어 존재 확인(없으면 AUCTION001)과 전체 입찰 수를 함께 얻는다 (existsById + count 쿼리 대체)
        Auction auction = auctionReader.get(auctionId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);

        // 하향식은 구매가 입찰 행을 만들지 않는다 → 조회할 입찰이 없다
        if (!(auction instanceof EnglishAuction english)) {
            return new AuctionBidHistoryResponseDto(List.of(), safePage, safeSize, 0, false);
        }

        //입찰조회 (최신순은 메서드 이름의 OrderByIdDesc, 인덱스 (auction_id, id DESC))
        Slice<Bid> bids = bidRepository.findByAuctionIdOrderByIdDesc(auctionId, PageRequest.of(safePage, safeSize));
        //입찰자id로 닉네임 한번에 조회
        Set<Long> bidderIds = bids.stream().map(Bid::getBidderId).collect(Collectors.toSet());
        Map<Long,String> nicknameMap = marketMemberRepository.findAllById(bidderIds).stream()
                .collect(Collectors.toMap(MarketMember::getId, MarketMember::getNickname));
        //dto로 변환
        List<AuctionBidHistoryItemDto> content = bids.stream().map(bid -> new AuctionBidHistoryItemDto(
                bid.getId(),
                maskNickname(nicknameMap.get(bid.getBidderId())),
                bid.getBidPrice(),
                bid.getBidAt(),
                bid.getBidderId().equals(memberId)
        )).toList();

        // totalElements는 경매의 입찰 수(입찰할 때마다 같은 트랜잭션에서 +1, 동시성 테스트 불변식 "bidCount = 입찰 행 수")
        return new AuctionBidHistoryResponseDto(content, safePage, safeSize, english.getBidCount(), bids.hasNext());
    }
    private String maskNickname(String nickname){
        if(nickname==null){return"알 수 없음";}
        if(nickname.length()<=1){return "*";}
        return nickname.charAt(0)+"**";
    }
}
