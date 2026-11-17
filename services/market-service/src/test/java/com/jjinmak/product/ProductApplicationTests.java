package com.jjinmak.product;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 상품·후기 서비스가 모놀리식 없이 혼자 뜨는지 확인한다. (DB는 H2, Kafka 리스너는 시작하지 않음)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.listener.auto-startup=false"
})
class ProductApplicationTests {

    @Test
    void contextLoads() {
    }
}
