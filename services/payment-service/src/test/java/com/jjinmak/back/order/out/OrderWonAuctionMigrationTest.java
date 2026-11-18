package com.jjinmak.back.order.out;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderWonAuctionMigrationTest {

    @Test
    @DisplayName("V9는 이미 있는 장바구니 항목과 주문의 경매 id를 받은 경매로 중복 없이 채운다")
    void v9_backfillsWonAuctionsFromCartItemsAndOrders() {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:orderwonauctionbackfill;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            flyway(dataSource, "8").migrate();

            jdbcTemplate.update("insert into order_member (id, created_at, updated_at, uuid, nickname) values (1, now(), now(), ?, '낙찰자')", UUID.randomUUID());
            jdbcTemplate.update("insert into order_member (id, created_at, updated_at, uuid, nickname) values (2, now(), now(), ?, '판매자')", UUID.randomUUID());
            jdbcTemplate.update("""
                    insert into cart_item (created_at, updated_at, winner_id, seller_id, auction_id, product_id, winning_price, delivery_fee, win_at, payment_due_at)
                    values (now(), now(), 1, 2, 10, 20, 30000, 3000, now(), now()),
                           (now(), now(), 1, 2, 11, 21, 50000, 4000, now(), now())
                    """);
            jdbcTemplate.update("insert into order_group (id, created_at, updated_at, total_price, winner_id) values (1, now(), now(), 33000, 1), (2, now(), now(), 66000, 1)");
            jdbcTemplate.update("""
                    insert into orders (created_at, updated_at, group_id, winner_id, seller_id, auction_id, product_id, winning_price, delivery_fee, state)
                    values (now(), now(), 1, 1, 2, 10, 20, 30000, 3000, 'WAITING'),
                           (now(), now(), 2, 1, 2, 10, 20, 30000, 3000, 'WAITING'),
                           (now(), now(), 2, 1, 2, 30, 40, 33000, 3000, 'PAID')
                    """);

            flyway(dataSource, "9").migrate();

            assertThat(jdbcTemplate.queryForList("select id from order_won_auction order by id", Long.class))
                    .containsExactly(10L, 11L, 30L);
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
