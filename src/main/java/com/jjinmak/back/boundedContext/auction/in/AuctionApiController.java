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

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionApiController {
    private final AuctionFacade auctionFacade;

    // 인증 방식이 정해진 후에, 접속 유저 정보 가져오기
    private final UUID sellerDev = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

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
