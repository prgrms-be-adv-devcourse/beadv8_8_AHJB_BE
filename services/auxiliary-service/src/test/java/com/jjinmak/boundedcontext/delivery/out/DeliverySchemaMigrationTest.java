package com.jjinmak.boundedcontext.delivery.out;

import com.jjinmak.boundedcontext.delivery.domain.Delivery;
import com.jjinmak.boundedcontext.delivery.domain.DeliveryMember;
import com.jjinmak.boundedcontext.delivery.in.DeliveryScheduler;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
@Import(DeliverySchemaMigrationTest.PostgresContainerConfig.class)
class DeliverySchemaMigrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresContainerConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }
    }

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    DeliveryScheduler deliveryScheduler;

    @Autowired
    PostgreSQLContainer postgres;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DeliveryMemberRepository deliveryMemberRepository;

    @Autowired
    DeliveryRepository deliveryRepository;

    @AfterEach
    void tearDown() {
        deliveryRepository.deleteAllInBatch();
        deliveryMemberRepository.deleteAllInBatch();
        jdbcTemplate.execute("drop schema if exists pre_v5 cascade");
    }

    @Test
    @DisplayName("Flyway로 만든 스키마가 엔티티 검증(validate)을 통과하고, 새 배송은 발급한 콜백 토큰과 함께 저장된다")
    void migratedSchema_passesValidationAndStoresCallbackToken() {
        DeliveryMember winner = deliveryMemberRepository.save(new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"));
        DeliveryMember seller = deliveryMemberRepository.save(new DeliveryMember(2L, UUID.randomUUID(), "판매자"));
        Delivery delivery = deliveryRepository.save(new Delivery(100L, winner, seller, CJ, "1111"));

        UUID stored = jdbcTemplate.queryForObject("select callback_token from delivery where id = ?", UUID.class, delivery.getId());

        assertThat(stored).isEqualTo(delivery.getCallbackToken());
    }

    @Test
    @DisplayName("V5를 적용하면 그 전에 있던 배송마다 서로 다른 콜백 토큰이 채워지고, 컬럼은 uuid NOT NULL이며 기본값이 남지 않는다")
    void v5_fillsCallbackTokensForExistingDeliveries() {
        flyway("4").migrate();
        jdbcTemplate.update("insert into pre_v5.delivery_member (id, created_at, updated_at, uuid, nickname) values (1, now(), now(), gen_random_uuid(), '낙찰자'), (2, now(), now(), gen_random_uuid(), '판매자')");
        jdbcTemplate.update("insert into pre_v5.delivery (created_at, updated_at, order_id, winner_id, seller_id, carrier, waybill, status) values (now(), now(), 100, 1, 2, 'CJ', '1111', 'WAITING'), (now(), now(), 101, 1, 2, 'CJ', '2222', 'IN_TRANSIT'), (now(), now(), 102, 1, 2, 'HANJIN', '3333', 'DELIVERED')");

        flyway("latest").migrate();

        Map<String, Object> tokens = jdbcTemplate.queryForMap("select count(*) as total, count(distinct callback_token) as distinct_tokens, count(*) filter (where callback_token is null) as null_tokens from pre_v5.delivery");
        assertThat(tokens).containsEntry("total", 3L).containsEntry("distinct_tokens", 3L).containsEntry("null_tokens", 0L);

        Map<String, Object> column = jdbcTemplate.queryForMap("select data_type, is_nullable, column_default from information_schema.columns where table_schema = 'pre_v5' and table_name = 'delivery' and column_name = 'callback_token'");
        assertThat(column).containsEntry("data_type", "uuid").containsEntry("is_nullable", "NO");
        assertThat(column.get("column_default")).isNull();
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("pre_v5")
                .locations("classpath:db/migration")
                .outOfOrder(true)
                .target(target)
                .load();
    }
}
