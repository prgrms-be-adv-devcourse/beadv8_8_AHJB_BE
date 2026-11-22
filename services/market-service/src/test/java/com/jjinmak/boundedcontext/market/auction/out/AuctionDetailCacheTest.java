package com.jjinmak.boundedcontext.market.auction.out;

import com.github.benmanes.caffeine.cache.Ticker;
import com.jjinmak.boundedcontext.market.auction.domain.Auction;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.jjinmak.boundedcontext.market.auction.exception.AuctionErrorCode.AUCTION_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionDetailCacheTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 12, 0);
    private static final long TTL_MS = 1_000;

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;
    private final AtomicInteger loads = new AtomicInteger();

    private final Function<Long, Auction> loader = id -> {
        loads.incrementAndGet();
        return EnglishAuction.create(1L, 100L, 10_000L, null, 3_000L, "CONSOLE", NOW.plusDays(1), NOW);
    };

    private AuctionDetailCache 캐시(boolean enabled) {
        return new AuctionDetailCache(enabled, TTL_MS, ticker, new SimpleMeterRegistry());
    }

    private void 시간이_흐른다(long millis) {
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    @Test
    void TTL_안에서는_DB를_다시_읽지_않는다() {
        AuctionDetailCache cache = 캐시(true);

        Auction first = cache.get(1L, loader);
        시간이_흐른다(TTL_MS - 1);
        Auction second = cache.get(1L, loader);

        assertThat(loads).hasValue(1);
        assertThat(second).isSameAs(first);
    }

    @Test
    void TTL이_지나면_다시_읽는다() {
        AuctionDetailCache cache = 캐시(true);

        cache.get(1L, loader);
        시간이_흐른다(TTL_MS + 1);
        cache.get(1L, loader);

        assertThat(loads).hasValue(2);
    }

    @Test
    void 상태_변경_이벤트를_받으면_그_경매만_지운다() {
        AuctionDetailCache cache = 캐시(true);
        cache.get(1L, loader);
        cache.get(2L, loader);

        cache.on(new AuctionFailedEvent(1L, 1L, "NO_BID", NOW));
        cache.get(1L, loader);
        cache.get(2L, loader);

        assertThat(loads).hasValue(3);   // 1번만 다시 읽음
    }

    @Test
    void 없는_경매는_예외를_그대로_던지고_캐시하지_않는다() {
        AuctionDetailCache cache = 캐시(true);
        Function<Long, Auction> notFound = id -> {
            loads.incrementAndGet();
            throw new BusinessException(AUCTION_NOT_FOUND);
        };

        assertThatThrownBy(() -> cache.get(9L, notFound)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> cache.get(9L, notFound)).isInstanceOf(BusinessException.class);

        assertThat(loads).hasValue(2);
    }

    @Test
    void 꺼져_있으면_매번_DB에서_읽는다() {
        AuctionDetailCache cache = 캐시(false);

        cache.get(1L, loader);
        cache.get(1L, loader);

        assertThat(loads).hasValue(2);
    }
}
