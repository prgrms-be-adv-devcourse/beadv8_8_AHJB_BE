package com.jjinmak.recommend.in;

import com.jjinmak.recommend.app.RecommendFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RecommendCfSimilaritySchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RecommendFacade.class, () -> mock(RecommendFacade.class))
            .withUserConfiguration(RecommendCfSimilarityScheduler.class);

    @Test
    @DisplayName("설정값이 없으면 협업 유사도 스케줄러가 켜진다")
    void schedulerIsEnabledByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(RecommendCfSimilarityScheduler.class));
    }

    @Test
    @DisplayName("recommend.cf-scheduler.enabled가 false면 협업 유사도 스케줄러가 꺼진다")
    void schedulerIsDisabledByProperty() {
        contextRunner.withPropertyValues("recommend.cf-scheduler.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RecommendCfSimilarityScheduler.class));
    }

    @Test
    @DisplayName("임베딩 스케줄러를 끄는 설정은 협업 유사도 스케줄러에 영향을 주지 않는다")
    void embeddingSchedulerPropertyDoesNotTurnItOff() {
        contextRunner.withPropertyValues("recommend.embedding-scheduler.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(RecommendCfSimilarityScheduler.class));
    }

    @Test
    @DisplayName("실행되면 Facade의 협업 유사도 계산을 호출한다")
    void refreshCfSimilarity_callsFacade() {
        RecommendFacade recommendFacade = mock(RecommendFacade.class);

        new RecommendCfSimilarityScheduler(recommendFacade).refreshCfSimilarity();

        verify(recommendFacade).refreshCfSimilarity();
    }

    @Test
    @DisplayName("1시간 간격으로 실행된다")
    void refreshCfSimilarity_runsEveryHour() throws NoSuchMethodException {
        Scheduled scheduled = RecommendCfSimilarityScheduler.class.getMethod("refreshCfSimilarity").getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelay()).isEqualTo(3_600_000L);
    }
}
