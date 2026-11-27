package com.jjinmak.boundedcontext.market.auction.out;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionStreamRegistryTest {

    private final AuctionStreamRegistry registry = new AuctionStreamRegistry(3, new SimpleMeterRegistry());

    @Test
    void 경매별로_연결을_세고_전체_수도_센다() {
        registry.add(1L, "tab-0001", new SseEmitter());
        registry.add(1L, "tab-0002", new SseEmitter());
        registry.add(2L, "tab-0003", new SseEmitter());

        assertThat(registry.viewerCount(1L)).isEqualTo(2);
        assertThat(registry.viewerCount(2L)).isEqualTo(1);
        assertThat(registry.total()).isEqualTo(3);
        assertThat(registry.auctionIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 상한을_넘으면_등록하지_않는다() {
        registry.add(1L, "tab-0001", new SseEmitter());
        registry.add(1L, "tab-0002", new SseEmitter());
        registry.add(1L, "tab-0003", new SseEmitter());

        assertThat(registry.add(1L, "tab-0004", new SseEmitter()).added()).isFalse();
        assertThat(registry.total()).isEqualTo(3);
        assertThat(registry.viewerCount(1L)).isEqualTo(3);
    }

    @Test
    void 같은_탭이_다시_연결하면_이전_연결을_바꾸고_수는_그대로다() {
        SseEmitter before = new SseEmitter();
        SseEmitter after = new SseEmitter();
        registry.add(1L, "tab-0001", before);

        AuctionStreamRegistry.Registration registration = registry.add(1L, "tab-0001", after);

        assertThat(registration.added()).isTrue();
        assertThat(registration.replaced()).isSameAs(before);
        assertThat(registry.viewerCount(1L)).isEqualTo(1);
        assertThat(registry.total()).isEqualTo(1);
        assertThat(registry.emitters(1L)).containsExactly(after);
    }

    @Test
    void 상한이_차도_같은_탭의_재연결은_받는다() {
        registry.add(1L, "tab-0001", new SseEmitter());
        registry.add(1L, "tab-0002", new SseEmitter());
        registry.add(1L, "tab-0003", new SseEmitter());

        assertThat(registry.add(1L, "tab-0001", new SseEmitter()).added()).isTrue();
        assertThat(registry.total()).isEqualTo(3);
    }

    @Test
    void 바뀐_이전_연결을_지워도_새_연결은_남는다() {
        SseEmitter before = new SseEmitter();
        SseEmitter after = new SseEmitter();
        registry.add(1L, "tab-0001", before);
        registry.add(1L, "tab-0001", after);

        registry.remove(1L, before);   // 이전 연결의 완료 콜백이 늦게 온 경우

        assertThat(registry.emitters(1L)).containsExactly(after);
        assertThat(registry.total()).isEqualTo(1);
    }

    @Test
    void 같은_연결을_두_번_지워도_한_번만_줄인다() {
        SseEmitter emitter = new SseEmitter();
        registry.add(1L, "tab-0001", emitter);
        registry.add(1L, "tab-0002", new SseEmitter());

        registry.remove(1L, emitter);
        registry.remove(1L, emitter);   // 완료·타임아웃 콜백이 겹친 경우

        assertThat(registry.total()).isEqualTo(1);
        assertThat(registry.viewerCount(1L)).isEqualTo(1);
    }

    @Test
    void 마지막_연결이_빠지면_경매도_목록에서_빠진다() {
        SseEmitter emitter = new SseEmitter();
        registry.add(1L, "tab-0001", emitter);

        registry.remove(1L, emitter);

        assertThat(registry.auctionIds()).isEmpty();
        assertThat(registry.emitters(1L)).isEmpty();
    }
}
