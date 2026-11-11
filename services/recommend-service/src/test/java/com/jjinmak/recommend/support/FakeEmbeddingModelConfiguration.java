package com.jjinmak.recommend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class FakeEmbeddingModelConfiguration {

    @Bean
    FakeEmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }
}
