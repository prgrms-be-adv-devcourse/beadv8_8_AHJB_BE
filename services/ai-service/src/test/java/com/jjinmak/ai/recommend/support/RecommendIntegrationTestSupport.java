package com.jjinmak.ai.recommend.support;

import com.jjinmak.ai.search.out.elasticsearch.ProductSearchIndexInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class, FakeChatModelConfiguration.class})
public abstract class RecommendIntegrationTestSupport {

    @MockitoBean
    ProductSearchIndexInitializer productSearchIndexInitializer;
}
