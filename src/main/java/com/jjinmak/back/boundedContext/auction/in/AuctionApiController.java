package com.jjinmak.back.boundedContext.auction.in;

import com.jjinmak.back.boundedContext.auction.app.AuctionFacade;
import com.jjinmak.back.boundedContext.auction.dto.AuctionBidResponseDto;
import com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode;
import com.jjinmak.back.boundedContext.auction.in.dto.AuctionBidRequestDto;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionApiController {
    private final AuctionFacade auctionFacade;

    //입찰메서드 추가하기
    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<RsData<AuctionBidResponseDto>> bid(
            @PathVariable Long auctionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody AuctionBidRequestDto request
    ){
        AuctionBidResponseDto response;
        try{
            response = auctionFacade.bid(auctionId,principal.memberId(),request.bidPrice());
        }catch (ObjectOptimisticLockingFailureException e){
            throw new BusinessException(AuctionErrorCode.BID_CONFLICT);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(new RsData<>(true,"입찰이 완료되었습니다.",response));
    }
}
