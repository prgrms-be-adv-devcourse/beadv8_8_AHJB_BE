package com.jjinmak.ai.recommend.config;

import com.jjinmak.ai.recommend.support.TestcontainersConfiguration;
import com.jjinmak.ai.search.out.elasticsearch.ProductSearchIndexInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.config.location=classpath:/application.yaml",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "recommend.embedding-scheduler.enabled=false",
        "recommend.cf-scheduler.enabled=false",
        "OPENAI_API_KEY=test-key",
        "ES_URIS=http://localhost:1"
})
@Import(TestcontainersConfiguration.class)
class AiModelConfigTest {

    @Autowired
    ApplicationContext context;

    @MockitoBean
    ProductSearchIndexInitializer productSearchIndexInitializer;

    @Test
    @DisplayName("운영 설정만으로 띄우면 Spring AI 모델 빈은 OpenAI 채팅 모델과 임베딩 모델 둘뿐이다")
    void mainConfig_createsOnlyChatAndEmbeddingModels() {
        assertThat(context.getBeanNamesForType(Model.class))
                .containsExactlyInAnyOrder("openAiChatModel", "openAiEmbeddingModel");
    }
}
