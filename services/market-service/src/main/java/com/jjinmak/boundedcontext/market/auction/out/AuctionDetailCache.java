package com.jjinmak.boundedcontext.market.auction.out;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.jjinmak.boundedcontext.market.auction.domain.Auction;
import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.function.Function;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

// 경매 상세의 공용 부분 캐시 (#557).
// 시청자 n명이 같은 경매를 폴링하면 같은 행을 n번 읽는다 → 경매 하나를 짧게(기본 1초) 들고 있다가 같이 쓴다.
//
// - 값은 DB에서 읽은 경매 엔티티(분리 상태). 상세 DTO는 요청마다 now·회원 id로 새로 만든다.
//   (하향식 가격은 시각의 함수, me는 회원마다 다름 → DTO를 캐시하면 틀린 값이 나간다)
//   ⚠ 읽기 전용: 캐시에서 꺼낸 경매를 바꾸지 않는다. 쓰기 경로는 항상 AuctionReader로 새로 읽는다
// - 같은 경매를 동시에 처음 읽으면 한 요청만 DB에 가고 나머지는 그 결과를 기다린다 (Caffeine get)
// - 입찰·낙찰·유찰이 커밋되면 그 경매를 지운다. 이벤트가 없는 변경(결제 완료·구매 확정 등)은 TTL로 늦어도 1초 안에 반영
// - 커밋 직전에 읽은 옛 값이 삭제 직후 다시 들어갈 수 있다 → TTL(1초)만큼만 늦다. 폴링 주기(2초)보다 짧아 허용
// - 서버 1대 기준(로컬 캐시). 서버가 늘면 삭제를 모든 서버에 알려야 한다
@Component
public class AuctionDetailCache {

    private static final int MAX_SIZE = 10_000;

    private final boolean enabled;
    private final Cache<Long, Auction> cache;

    @Autowired
    public AuctionDetailCache(@Value("${market.auction.detail-cache.enabled:true}") boolean enabled,
                              @Value("${market.auction.detail-cache.ttl-ms:1000}") long ttlMs,
                              ObjectProvider<MeterRegistry> meterRegistry) {
        this(enabled, ttlMs, Ticker.systemTicker(), meterRegistry.getIfAvailable());
    }

    AuctionDetailCache(boolean enabled, long ttlMs, Ticker ticker, MeterRegistry meterRegistry) {
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(ttlMs))
                .maximumSize(MAX_SIZE)
                .ticker(ticker)
                .recordStats()
                .build();
        if (meterRegistry != null) {
            // cache_gets_total{result=hit|miss}, cache_evictions_total 등 (name=auction.detail)
            CaffeineCacheMetrics.monitor(meterRegistry, cache, "auction.detail");
        }
    }

    // 없으면 loader로 읽어 넣는다. loader 예외(없는 경매 등)는 그대로 던지고 캐시하지 않는다
    public Auction get(Long auctionId, Function<Long, Auction> loader) {
        if (!enabled) {
            return loader.apply(auctionId);
        }
        return cache.get(auctionId, loader);
    }

    public void evict(Long auctionId) {
        cache.invalidate(auctionId);
    }

    // 상태를 바꾼 트랜잭션이 커밋된 뒤에 지운다 (롤백되면 지우지 않는다)
    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void on(AuctionBidSucceedEvent event) {
        evict(event.auctionId());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void on(AuctionWonEvent event) {
        evict(event.auctionId());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void on(AuctionFailedEvent event) {
        evict(event.auctionId());
    }
}
