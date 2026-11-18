package com.jjinmak.back.boundedContext.auction.app;


import com.jjinmak.back.boundedContext.auction.domain.AuctionMember;
import com.jjinmak.back.boundedContext.auction.domain.Bid;
import com.jjinmak.back.boundedContext.auction.dto.AuctionBidHistoryItemDto;
import com.jjinmak.back.boundedContext.auction.dto.AuctionBidHistoryResponseDto;
import com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode;
import com.jjinmak.back.boundedContext.auction.out.AuctionMemberRepository;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import com.jjinmak.back.boundedContext.auction.out.BidRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuctionBidHistoryUseCase {
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionMemberRepository auctionMemberRepository;

    public AuctionBidHistoryResponseDto bidHistory(Long auctionId,Long memberId,int page,int size){
        if(!auctionRepository.existsById(auctionId)){
            throw new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND);
        }
        //페이지 조건(최신순,size최대50)
        PageRequest pageRequest = PageRequest.of(Math.max(page,0),Math.min(Math.max(size,1),50), Sort.by(Sort.Direction.DESC,"id"));
        //입찰조회
        Page<Bid> bids = bidRepository.findByAuctionId(auctionId,pageRequest);
        //입찰자id로 닉네임 한번에 조회
        Set<Long> bidderIds = bids.stream().map(Bid::getBidderId).collect(Collectors.toSet());
        Map<Long,String> nicknameMap = auctionMemberRepository.findAllById(bidderIds).stream()
                .collect(Collectors.toMap(AuctionMember::getId, AuctionMember::getNickname));
        //dto로 변환
        List<AuctionBidHistoryItemDto> content = bids.stream().map(bid -> new AuctionBidHistoryItemDto(
                bid.getId(),
                maskNickname(nicknameMap.get(bid.getBidderId())),
                bid.getBidPrice(),
                bid.getBidAt(),
                bid.getBidderId().equals(memberId)
        )).toList();


        return new AuctionBidHistoryResponseDto(content,bids.getNumber(),bids.getSize(),bids.getTotalElements(),bids.hasNext());
    }
    private String maskNickname(String nickname){
        if(nickname==null){return"알 수 없음";}
        if(nickname.length()<=1){return "*";}
        return nickname.charAt(0)+"**";
    }
}
