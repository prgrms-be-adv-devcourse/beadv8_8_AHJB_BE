package com.jjinmak.boundedcontext.market.auction.out;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionStreamRegistryTest {

    private final AuctionStreamRegistry registry = new AuctionStreamRegistry(3, new SimpleMeterRegistry());

    @Test
    void 경매별로_연결을_세고_전체_수도_센다() {
        registry.add(1L, new SseEmitter());
        registry.add(1L, new SseEmitter());
        registry.add(2L, new SseEmitter());

        assertThat(registry.viewerCount(1L)).isEqualTo(2);
        assertThat(registry.viewerCount(2L)).isEqualTo(1);
        assertThat(registry.total()).isEqualTo(3);
        assertThat(registry.auctionIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 상한을_넘으면_등록하지_않는다() {
        registry.add(1L, new SseEmitter());
        registry.add(1L, new SseEmitter());
        registry.add(1L, new SseEmitter());

        assertThat(registry.add(1L, new SseEmitter())).isFalse();
        assertThat(registry.total()).isEqualTo(3);
        assertThat(registry.viewerCount(1L)).isEqualTo(3);
    }

    @Test
    void 같은_연결을_두_번_지워도_한_번만_줄인다() {
        SseEmitter emitter = new SseEmitter();
        registry.add(1L, emitter);
        registry.add(1L, new SseEmitter());

        registry.remove(1L, emitter);
        registry.remove(1L, emitter);   // 완료·타임아웃 콜백이 겹친 경우

        assertThat(registry.total()).isEqualTo(1);
        assertThat(registry.viewerCount(1L)).isEqualTo(1);
    }

    @Test
    void 마지막_연결이_빠지면_경매도_목록에서_빠진다() {
        SseEmitter emitter = new SseEmitter();
        registry.add(1L, emitter);

        registry.remove(1L, emitter);

        assertThat(registry.auctionIds()).isEmpty();
        assertThat(registry.emitters(1L)).isEmpty();
    }
}
