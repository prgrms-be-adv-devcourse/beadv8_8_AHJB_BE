package com.jjinmak.boundedcontext.market.auction.out;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// 경매별 실시간 구독(SSE) 연결 보관소 (#557). 서버 메모리에만 있다 → 서버 1대 기준.
// 서버가 늘면 다른 서버가 처리한 입찰을 받도록 Redis Pub/Sub 같은 전달 통로가 필요하다.
//
// - 서버 전체 연결 수에 상한을 둔다(기본 2,000). 넘으면 구독을 거절하고 프론트는 폴링으로 대체한다 (512MiB 보호)
// - 경매별 집합 추가·삭제는 compute로 원자적으로 한다 (빈 집합을 지우는 순간 다른 연결이 들어와도 잃지 않게)
// - 한 연결이 두 번 지워져도(완료·오류·타임아웃 콜백이 겹침) 전체 수는 한 번만 줄인다
@Component
public class AuctionStreamRegistry {

    private final int maxConnections;
    private final Map<Long, Set<SseEmitter>> byAuction = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    @Autowired
    public AuctionStreamRegistry(@Value("${market.auction.stream.max-connections:2000}") int maxConnections,
                                 ObjectProvider<MeterRegistry> meterRegistry) {
        this(maxConnections, meterRegistry.getIfAvailable());
    }

    AuctionStreamRegistry(int maxConnections, MeterRegistry meterRegistry) {
        this.maxConnections = maxConnections;
        if (meterRegistry != null) {
            Gauge.builder("auction.stream.connections", total, AtomicInteger::get)
                    .description("경매 실시간 구독(SSE) 연결 수")
                    .register(meterRegistry);
        }
    }

    // 상한을 넘으면 false (등록하지 않음)
    public boolean add(Long auctionId, SseEmitter emitter) {
        if (total.incrementAndGet() > maxConnections) {
            total.decrementAndGet();
            return false;
        }
        byAuction.compute(auctionId, (id, emitters) -> {
            Set<SseEmitter> set = emitters != null ? emitters : ConcurrentHashMap.newKeySet();
            set.add(emitter);
            return set;
        });
        return true;
    }

    public void remove(Long auctionId, SseEmitter emitter) {
        AtomicBoolean removed = new AtomicBoolean();
        byAuction.computeIfPresent(auctionId, (id, emitters) -> {
            removed.set(emitters.remove(emitter));
            return emitters.isEmpty() ? null : emitters;
        });
        if (removed.get()) {
            total.decrementAndGet();
        }
    }

    public List<SseEmitter> emitters(Long auctionId) {
        Set<SseEmitter> emitters = byAuction.get(auctionId);
        return emitters == null ? List.of() : List.copyOf(emitters);
    }

    public int viewerCount(Long auctionId) {
        Set<SseEmitter> emitters = byAuction.get(auctionId);
        return emitters == null ? 0 : emitters.size();
    }

    public Set<Long> auctionIds() {
        return Set.copyOf(byAuction.keySet());
    }

    public int total() {
        return total.get();
    }
}
