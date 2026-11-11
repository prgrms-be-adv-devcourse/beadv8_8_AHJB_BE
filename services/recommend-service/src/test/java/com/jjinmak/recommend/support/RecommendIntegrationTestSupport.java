package com.jjinmak.recommend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class})
public abstract class RecommendIntegrationTestSupport {
}
