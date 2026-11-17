package com.jjinmak.auction.in;

import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 상품이 전환하기 전에 보내는 JSON을 새 계약으로 받을 수 있어야 한다
class ProductRegisteredEventJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void 옛_형태_JSON을_받으면_새_필드는_null이다() {
        String json = """
                {"productId":1,"sellerId":100,"shippingFee":3000,"startPrice":10000,
                 "instantWinPrice":50000,"duration":3,"customEndAt":null,
                 "productName":"상품","category":"CONSOLE","thumbnailUrl":null}
                """;

        ProductRegisteredEvent event = mapper.readValue(json, ProductRegisteredEvent.class);

        assertThat(event.duration()).isEqualTo(3);
        assertThat(event.instantWinPrice()).isEqualTo(50_000L);
        assertThat(event.auctionType()).isNull();
        assertThat(event.scheduledAt()).isNull();
        assertThat(event.english()).isNull();
        assertThat(event.dutch()).isNull();
    }

    @Test
    void 새_형태는_보낸_그대로_다시_읽힌다() {
        ProductRegisteredEvent sent = new ProductRegisteredEvent(1L, 100L, 3_000L, 300_000L, null, 0, null,
                null, "CONSOLE", null, "DUTCH", LocalDateTime.of(2026, 11, 20, 21, 0),
                null, new ProductRegisteredEvent.DutchTerms(150_000L, 2, 60));

        ProductRegisteredEvent received =
                mapper.readValue(mapper.writeValueAsString(sent), ProductRegisteredEvent.class);

        assertThat(received).isEqualTo(sent);
    }
}
