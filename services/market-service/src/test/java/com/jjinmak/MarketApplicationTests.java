package com.jjinmak;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 상품·후기·경매가 한 애플리케이션으로 뜨는지 확인한다. (DB는 H2, Kafka 리스너는 시작하지 않음)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:market;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.listener.auto-startup=false"
})
class MarketApplicationTests {

    @Test
    void contextLoads() {
    }
}
