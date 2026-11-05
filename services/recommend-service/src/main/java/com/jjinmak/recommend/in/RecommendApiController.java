package com.jjinmak.recommend.in;

import com.jjinmak.recommend.app.RecommendFacade;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "추천", description = "회원 맞춤 경매 상품을 추천하는 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommend")
public class RecommendApiController {

    private final RecommendFacade recommendFacade;
}
