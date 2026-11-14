package com.jjinmak.recommend.in;

import com.jjinmak.recommend.app.RecommendFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recommend.cf-scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RecommendCfSimilarityScheduler {

    private final RecommendFacade recommendFacade;

    @Scheduled(fixedDelay = 3_600_000)
    public void refreshCfSimilarity(){
        recommendFacade.refreshCfSimilarity();
    }
}
