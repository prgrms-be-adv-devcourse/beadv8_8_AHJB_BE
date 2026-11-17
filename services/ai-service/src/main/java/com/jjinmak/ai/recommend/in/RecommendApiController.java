package com.jjinmak.ai.recommend.in;

import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import com.jjinmak.ai.recommend.app.RecommendFacade;
import com.jjinmak.ai.recommend.app.dto.AuctionResponseDto;
import com.jjinmak.ai.recommend.app.dto.SearchRequestDto;
import com.jjinmak.ai.recommend.app.dto.SearchResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "추천", description = "회원 맞춤 경매 상품을 추천하는 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommend")
public class RecommendApiController {

    private final RecommendFacade recommendFacade;

    @Operation(
            summary = "비슷한 경매 추천",
            description = "경매 id에 해당하는 상품과 비슷한 진행 중 경매를 최대 10개 조회합니다."
    )
    @GetMapping("/auction/{auctionId}")
    public ResponseEntity<RsData<List<AuctionResponseDto>>> readRecommendedAuctionList(
            @PathVariable Long auctionId
    ){
        List<AuctionResponseDto> response = recommendFacade.readRecommendedAuctionList(auctionId);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "인기 경매 추천",
            description = "입찰 참여가 많고 마감이 가까운 진행 중 경매를 최대 10개 조회합니다."
    )
    @GetMapping("/popular")
    public ResponseEntity<RsData<List<AuctionResponseDto>>> readPopularAuctionList(){
        List<AuctionResponseDto> response = recommendFacade.readPopularAuctionList();
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "회원 맞춤 경매 추천",
            description = "회원의 낙찰, 입찰, 찜 기록을 바탕으로 진행 중 경매를 최대 10개 조회합니다. 기록이 부족하면 인기 경매로 채웁니다."
    )
    @GetMapping("/me")
    public ResponseEntity<RsData<List<AuctionResponseDto>>> readPersonalRecommendedAuctionList(
            @AuthenticationPrincipal AuthPrincipal authPrincipal
    ){
        Long memberId = authPrincipal.memberId();

        List<AuctionResponseDto> response = recommendFacade.readPersonalRecommendedAuctionList(memberId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "대화형 경매 검색",
            description = "자연어 질문에서 가격, 카테고리, 제조사, 키워드 조건을 찾아 진행 중 경매를 검색하고 요약과 함께 반환합니다. 이전 응답의 조건을 함께 보내면 이어서 검색합니다."
    )
    @PostMapping("/search")
    public ResponseEntity<RsData<SearchResponseDto>> searchAuctions(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @RequestBody SearchRequestDto request
    ){
        Long memberId = authPrincipal.memberId();

        SearchResponseDto response = recommendFacade.searchAuctions(memberId, request);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
