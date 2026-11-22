package com.jjinmak.back.order.out;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderAuctionUniqueMigrationTest {

    @Test
    @DisplayName("V17은 주문이 둘 이상인 경매가 든 결제 대기 그룹을 주문째 지우고 주문이 남지 않은 그룹을 지운 뒤 경매 id 유니크 제약을 건다. 결제 이후 상태의 주문과 겹치지 않는 결제 대기 주문은 남는다")
    void v17_removesDuplicateWaitingGroupsAndMakesAuctionUnique() {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:orderauctionunique;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            flyway(dataSource, "16").migrate();

            jdbcTemplate.update("insert into order_member (id, created_at, updated_at, uuid, nickname) values (1, now(), now(), ?, '낙찰자')", UUID.randomUUID());
            jdbcTemplate.update("insert into order_member (id, created_at, updated_at, uuid, nickname) values (2, now(), now(), ?, '판매자')", UUID.randomUUID());
            jdbcTemplate.update("""
                    insert into order_group (id, created_at, updated_at, total_price, winner_id, life_time)
                    values (31, now(), now(), 160000, 1, now()), (32, now(), now(), 160000, 1, now()), (33, now(), now(), 160000, 1, now()),
                           (40, now(), now(), 26000, 1, now()), (41, now(), now(), 13000, 1, now()),
                           (50, now(), now(), 13000, 1, now()), (60, now(), now(), 13000, 1, now())
                    """);
            jdbcTemplate.update("""
                    insert into orders (id, created_at, updated_at, group_id, winner_id, seller_id, auction_id, product_id, winning_price, delivery_fee, state)
                    values (47, now(), now(), 31, 1, 2, 100, 27, 157000, 3000, 'WAITING'),
                           (48, now(), now(), 32, 1, 2, 100, 27, 157000, 3000, 'PAID'),
                           (49, now(), now(), 33, 1, 2, 100, 27, 157000, 3000, 'WAITING'),
                           (70, now(), now(), 40, 1, 2, 200, 28, 10000, 3000, 'WAITING'),
                           (71, now(), now(), 40, 1, 2, 201, 29, 10000, 3000, 'WAITING'),
                           (72, now(), now(), 41, 1, 2, 200, 28, 10000, 3000, 'WAITING'),
                           (80, now(), now(), 50, 1, 2, 300, 30, 10000, 3000, 'WAITING'),
                           (90, now(), now(), 60, 1, 2, 400, 31, 10000, 3000, 'CONFIRMED')
                    """);

            flyway(dataSource, "17").migrate();

            assertThat(jdbcTemplate.queryForList("select id from orders order by id", Long.class)).containsExactly(48L, 80L, 90L);
            assertThat(jdbcTemplate.queryForList("select id from order_group order by id", Long.class)).containsExactly(32L, 50L, 60L);
            assertThatThrownBy(() -> jdbcTemplate.update("""
                    insert into orders (id, created_at, updated_at, group_id, winner_id, seller_id, auction_id, product_id, winning_price, delivery_fee, state)
                    values (91, now(), now(), 50, 1, 2, 400, 31, 10000, 3000, 'WAITING')
                    """))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private Flyway flyway(DataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }
}
