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
import java.util.concurrent.atomic.AtomicReference;

// 경매별 실시간 구독(SSE) 연결 보관소 (#557). 서버 메모리에만 있다 → 서버 1대 기준.
// 서버가 늘면 다른 서버가 처리한 입찰을 받도록 Redis Pub/Sub 같은 전달 통로가 필요하다.
//
// - 연결은 경매별로 "탭 ID(clientId) → 연결" 하나씩 둔다 (#566).
//   새로고침하면 서버는 이전 연결이 끊긴 것을 다음 쓰기가 실패할 때까지 모른다 → 같은 탭이 다시 오면 이전 연결을 바로 바꾼다.
//   그래서 시청자 수 = 탭 수 (새로고침으로 늘지 않음)
// - 서버 전체 연결 수에 상한을 둔다(기본 2,000). 새 탭만 상한을 검사한다 (같은 탭의 재연결은 수가 그대로라 항상 허용)
// - 경매별 Map 추가·삭제는 compute로 원자적으로 한다 (빈 Map을 지우는 순간 다른 연결이 들어와도 잃지 않게)
// - 삭제는 "지금 그 자리에 있는 연결이 이 연결일 때만" 한다: 바뀐 이전 연결의 완료 콜백이 늦게 와도 새 연결을 지우지 않게.
//   한 연결이 두 번 지워져도(완료·오류·타임아웃 콜백이 겹침) 전체 수는 한 번만 줄인다
@Component
public class AuctionStreamRegistry {

    private final int maxConnections;
    private final Map<Long, Map<String, SseEmitter>> byAuction = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    // 등록 결과. added=false면 상한 초과로 거절, replaced는 같은 탭의 이전 연결 (없으면 null)
    public record Registration(boolean added, SseEmitter replaced) {
        static final Registration REJECTED = new Registration(false, null);
    }

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

    public Registration add(Long auctionId, String clientId, SseEmitter emitter) {
        AtomicReference<SseEmitter> previous = new AtomicReference<>();
        AtomicBoolean rejected = new AtomicBoolean();
        byAuction.compute(auctionId, (id, clients) -> {
            boolean newClient = clients == null || !clients.containsKey(clientId);
            if (newClient && total.incrementAndGet() > maxConnections) {
                total.decrementAndGet();
                rejected.set(true);
                return clients;   // 그대로 (null이면 등록 안 됨)
            }
            Map<String, SseEmitter> map = clients != null ? clients : new ConcurrentHashMap<>();
            previous.set(map.put(clientId, emitter));
            return map;
        });
        return rejected.get() ? Registration.REJECTED : new Registration(true, previous.get());
    }

    // 지금 등록된 연결이 이 emitter일 때만 지운다 (같은 탭이 새로 연결했으면 아무것도 안 함)
    public void remove(Long auctionId, SseEmitter emitter) {
        AtomicBoolean removed = new AtomicBoolean();
        byAuction.computeIfPresent(auctionId, (id, clients) -> {
            removed.set(clients.values().removeIf(current -> current == emitter));
            return clients.isEmpty() ? null : clients;
        });
        if (removed.get()) {
            total.decrementAndGet();
        }
    }

    public List<SseEmitter> emitters(Long auctionId) {
        Map<String, SseEmitter> clients = byAuction.get(auctionId);
        return clients == null ? List.of() : List.copyOf(clients.values());
    }

    public int viewerCount(Long auctionId) {
        Map<String, SseEmitter> clients = byAuction.get(auctionId);
        return clients == null ? 0 : clients.size();
    }

    public Set<Long> auctionIds() {
        return Set.copyOf(byAuction.keySet());
    }

    public int total() {
        return total.get();
    }
}
