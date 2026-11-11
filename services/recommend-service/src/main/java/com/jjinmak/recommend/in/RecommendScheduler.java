package com.jjinmak.recommend.in;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.recommend.app.RecommendFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendScheduler {

    private final RecommendFacade recommendFacade;

    @Scheduled(fixedDelay = 600_000)
    public void requestEmbedding(){
        List<Long> productIds = recommendFacade.findPendingEmbedding();

        for (Long productId : productIds){
            try {
                recommendFacade.requestEmbedding(productId);
            } catch (BusinessException e){
                log.error("오류가 발생해 임베딩을 생성하지 못했습니다.\nproductId: {}\nerrorCode: {}", productId, e.getErrorCode());
            } catch (Exception e){
                log.error("임베딩 생성 실패.\nproductId: {}", productId, e);
            }
        }
    }
}
