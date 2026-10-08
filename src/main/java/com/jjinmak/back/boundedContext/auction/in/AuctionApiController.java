package com.jjinmak.back.boundedContext.auction.in;

import com.jjinmak.back.boundedContext.auction.app.AuctionFacade;
import com.jjinmak.back.boundedContext.auction.dto.AuctionCreateDto;
import com.jjinmak.back.boundedContext.auction.in.dto.AuctionReAuctionRequestDto;
import com.jjinmak.back.global.rsData.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionApiController {
    private final AuctionFacade auctionFacade;

    // 인증 방식이 정해진 후에, 접속 유저 정보 가져오기
    private final Long sellerDev = 1L;

    @PostMapping
    public ResponseEntity<RsData<Long>> reAuction(@Valid @RequestBody AuctionReAuctionRequestDto request){
        AuctionCreateDto dto = new AuctionCreateDto(
                request.productId(),
                sellerDev,
                request.startPrice(),
                request.instantWinPrice(),
                request.duration()
        );
        Long auctionId = auctionFacade.reAuction(dto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(auctionId));
    }
}
