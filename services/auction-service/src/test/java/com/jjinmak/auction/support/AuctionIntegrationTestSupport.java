package com.jjinmak.auction.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// 실제 PostgreSQL(컨테이너)로 도는 통합 테스트의 공통 설정.
// - 컨테이너는 static이라 모든 하위 테스트가 하나를 공유하고, 스프링 컨텍스트도 캐시돼 재사용된다
// - @ServiceConnection이 컨테이너 주소를 datasource 설정으로 넣어 준다 (local 프로필 불필요)
// - Docker가 없으면 실패 대신 건너뛴다 (CI ubuntu-latest에는 Docker가 있다)
// - Kafka는 띄우지 않는다: 리스너 자동 시작·토픽 생성을 끄고 KafkaTemplate은 mock으로 바꾼다
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
public abstract class AuctionIntegrationTestSupport {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @MockitoBean
    protected KafkaTemplate<String, Object> kafkaTemplate;
}
