package com.jjinmak.order.out;

import com.jjinmak.order.domain.OrderGroup;
import com.jjinmak.order.domain.OrderMember;
import com.jjinmak.order.out.repository.OrderGroupRepository;
import com.jjinmak.order.out.repository.OrderMemberRepository;
import com.jjinmak.order.out.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ordermigration;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class OrderSchemaMigrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OrderMemberRepository orderMemberRepository;

    @Autowired
    OrderGroupRepository orderGroupRepository;

    @Autowired
    OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAllInBatch();
        orderGroupRepository.deleteAllInBatch();
        orderMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Flyway로 만든 스키마가 엔티티 검증(validate)을 통과하고, 버전 없이 넣은 주문 행은 버전 0이 된다")
    void migratedSchema_passesValidationAndDefaultsVersionToZero() {
        OrderMember winner = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));
        OrderMember seller = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "판매자"));
        OrderGroup group = orderGroupRepository.save(new OrderGroup(winner, 33_000L));

        jdbcTemplate.update("""
                insert into orders (created_at, updated_at, group_id, winner_id, seller_id, auction_id, product_id, winning_price, delivery_fee, state)
                values (now(), now(), ?, ?, ?, 10, 20, 30000, 3000, 'PAID')
                """, group.getId(), winner.getId(), seller.getId());

        Long version = jdbcTemplate.queryForObject("select version from orders where group_id = ?", Long.class, group.getId());
        assertThat(version).isZero();
    }
}
