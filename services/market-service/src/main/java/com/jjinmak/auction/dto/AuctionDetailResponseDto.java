package com.jjinmak.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 상세 응답은 유형마다 모양이 다르다. 공통 틀: auction(모두에게 같은 값) / me(개인 값) / serverTime
@Schema(oneOf = {EnglishAuctionDetailResponseDto.class, DutchAuctionDetailResponseDto.class})
public sealed interface AuctionDetailResponseDto
        permits EnglishAuctionDetailResponseDto, DutchAuctionDetailResponseDto {
    LocalDateTime serverTime();
}