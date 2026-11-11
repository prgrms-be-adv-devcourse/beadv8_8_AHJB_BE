package com.jjinmak.recommend.in;

import com.jjinmak.recommend.app.RecommendFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RecommendSchedulerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RecommendFacade.class, () -> mock(RecommendFacade.class))
            .withUserConfiguration(RecommendScheduler.class);

    @Test
    @DisplayName("설정값이 없으면 임베딩 스케줄러가 켜진다")
    void schedulerIsEnabledByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(RecommendScheduler.class));
    }

    @Test
    @DisplayName("recommend.embedding-scheduler.enabled가 false면 임베딩 스케줄러가 꺼진다")
    void schedulerIsDisabledByProperty() {
        contextRunner.withPropertyValues("recommend.embedding-scheduler.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RecommendScheduler.class));
    }
}
