package com.jjinmak.back.payment.support;

import com.jjinmak.back.payment.out.toss.TossPaymentsClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// 실제 PostgreSQL(컨테이너)로 도는 통합 테스트의 공통 설정.
// Flyway 마이그레이션이 컨테이너에 그대로 적용되고 ddl-auto: validate 로 스키마까지 검증된다.
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.jpa.hibernate.ddl-auto=validate"   // 공통 테스트 설정의 create-drop 대신 Flyway 스키마를 검증
})
@Import(PaymentIntegrationTestSupport.PostgresContainerConfig.class)
public abstract class PaymentIntegrationTestSupport {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresContainerConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }
    }

    // Kafka 는 띄우지 않는다
    @MockitoBean
    protected KafkaTemplate<String, Object> kafkaTemplate;

    // 테스트에서 토스로 실제 요청이 나가지 않게 한다
    @MockitoBean
    protected TossPaymentsClient tossPaymentsClient;
}