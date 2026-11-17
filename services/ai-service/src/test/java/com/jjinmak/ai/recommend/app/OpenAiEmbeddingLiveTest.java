package com.jjinmak.ai.recommend.app;

import com.jjinmak.ai.recommend.support.FakeChatModelConfiguration;
import com.jjinmak.ai.recommend.support.TestcontainersConfiguration;
import com.jjinmak.ai.search.out.elasticsearch.ProductSearchIndexInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@SpringBootTest(properties = "spring.ai.model.embedding=openai")
@Import({TestcontainersConfiguration.class, FakeChatModelConfiguration.class})
class OpenAiEmbeddingLiveTest {

    @Autowired
    EmbeddingModel embeddingModel;

    @MockitoBean
    ProductSearchIndexInitializer productSearchIndexInitializer;

    @Test
    @DisplayName("실제 OpenAI 임베딩 모델에 dimensions 1536을 주면 1536차원 벡터가 온다")
    void realModel_returnsConfiguredDimensions() {
        float[] vector = embeddingModel.embed("상품명: 듀얼센스 / 카테고리: ACCESSORY / 제조사: SONY");

        assertThat(vector).hasSize(1536);
    }
}
