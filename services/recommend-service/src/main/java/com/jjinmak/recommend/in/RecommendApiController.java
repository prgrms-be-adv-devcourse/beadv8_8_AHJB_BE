package com.jjinmak.recommend.in;

import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import com.jjinmak.recommend.app.RecommendFacade;
import com.jjinmak.recommend.app.dto.AuctionResponseDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "추천", description = "회원 맞춤 경매 상품을 추천하는 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommend")
public class RecommendApiController {

    private final RecommendFacade recommendFacade;

    @GetMapping("/auction/{auctionId}")
    public ResponseEntity<RsData<List<AuctionResponseDto>>> readRecommendedAuctionList(
            @PathVariable Long auctionId
    ){
        List<AuctionResponseDto> response = recommendFacade.readRecommendedAuctionList(auctionId);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @GetMapping("/popular")
    public ResponseEntity<RsData<List<AuctionResponseDto>>> readPopularAuctionList(){
        List<AuctionResponseDto> response = recommendFacade.readPopularAuctionList();
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @GetMapping("/me")
    public ResponseEntity<RsData<List<AuctionResponseDto>>> readPersonalRecommendedAuctionList(
            @AuthenticationPrincipal AuthPrincipal authPrincipal
    ){
        Long memberId = authPrincipal.memberId();

        List<AuctionResponseDto> response = recommendFacade.readPersonalRecommendedAuctionList(memberId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
