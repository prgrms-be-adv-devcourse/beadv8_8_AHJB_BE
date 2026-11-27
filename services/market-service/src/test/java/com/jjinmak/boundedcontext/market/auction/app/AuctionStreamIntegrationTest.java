package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.market.auction.out.AuctionRepository;
import com.jjinmak.boundedcontext.market.auction.out.AuctionStreamRegistry;
import com.jjinmak.boundedcontext.market.auction.support.AuctionIntegrationTestSupport;
import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실시간 구독(SSE)을 실제 PostgreSQL·트랜잭션에서 확인한다 (#557).
// 연결 대신 보낸 이벤트를 기록하는 emitter를 보관소에 넣어, 커밋 후 무엇이 나가는지 본다
@TestPropertySource(properties = "market.auction.stream.max-connections=3")
class AuctionStreamIntegrationTest extends AuctionIntegrationTestSupport {

    private static final Long SELLER_ID = 100L;
    private static final Long BIDDER_ID = 200L;

    @Autowired AuctionFacade auctionFacade;
    @Autowired AuctionStreamUseCase auctionStreamUseCase;
    @Autowired AuctionStreamRegistry registry;
    @Autowired AuctionRepository auctionRepository;

    private final List<Long> created = new ArrayList<>();
    private final List<Registered> registered = new ArrayList<>();

    private record Registered(Long auctionId, SseEmitter emitter) {
    }

    // 실제 응답 대신 보낸 이벤트 글자를 모은다 ("event:auction\ndata:{...}")
    static class RecordingEmitter extends SseEmitter {
        final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            events.add(builder.build().stream()
                    .map(data -> String.valueOf(data.getData()))
                    .collect(Collectors.joining()));
        }

        boolean has(String event) {
            return events.stream().anyMatch(text -> text.contains("event:" + event));
        }
    }

    @AfterEach
    void tearDown() {
        registered.forEach(r -> registry.remove(r.auctionId(), r.emitter()));
        for (Long id : created) {
            jdbcTemplate.update("delete from bid where auction_id = ?", id);
            jdbcTemplate.update("delete from auction where id = ?", id);
        }
    }

    private Long 진행중_경매() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        Long id = auctionRepository.save(EnglishAuction.create(557_100L + created.size(), SELLER_ID, 10_000L, 50_000L,
                3_000L, "CONSOLE", now.plusDays(1), now.minusHours(1))).getId();
        created.add(id);
        return id;
    }

    private RecordingEmitter 구독자(Long auctionId) {
        RecordingEmitter emitter = new RecordingEmitter();
        registry.add(auctionId, "test-tab-" + registered.size(), emitter);
        registered.add(new Registered(auctionId, emitter));
        return emitter;
    }

    // 전송은 전용 스레드에서 비동기로 일어난다
    private static boolean 기다린다(Predicate<Void> condition) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (condition.test(null)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    @Test
    void 입찰이_커밋되면_새_입찰과_경매_상태를_보낸다() throws InterruptedException {
        Long id = 진행중_경매();
        RecordingEmitter viewer = 구독자(id);

        auctionFacade.bid(id, BIDDER_ID, 20_000L);

        assertThat(기다린다(v -> viewer.has("bid") && viewer.has("auction"))).isTrue();
        assertThat(viewer.events).anyMatch(text -> text.contains("event:auction") && text.contains("\"currentPrice\":20000"));
        assertThat(viewer.events).anyMatch(text -> text.contains("event:bid") && text.contains("\"bidPrice\":20000"));
        assertThat(viewer.events).noneMatch(text -> text.contains("isMine"));
    }

    @Test
    void 실패한_입찰은_보내지_않는다() throws InterruptedException {
        Long id = 진행중_경매();
        RecordingEmitter viewer = 구독자(id);

        // 판매자 본인 입찰 → 업무 예외로 롤백
        assertThatThrownBy(() -> auctionFacade.bid(id, SELLER_ID, 20_000L)).isInstanceOf(BusinessException.class);

        Thread.sleep(500);
        assertThat(viewer.has("bid")).isFalse();
        assertThat(viewer.has("auction")).isFalse();
    }

    @Test
    void 구독하면_보관소에_등록되고_상한을_넘으면_거절한다() {
        Long id = 진행중_경매();
        for (int i = 0; i < 3; i++) {
            Optional<SseEmitter> emitter = auctionFacade.subscribe(id, "tab-000" + i);
            assertThat(emitter).isPresent();
            registered.add(new Registered(id, emitter.get()));
        }

        assertThat(registry.viewerCount(id)).isEqualTo(3);
        assertThat(auctionFacade.subscribe(id, "tab-0009")).isEmpty();
    }

    @Test
    void 같은_탭에서_다시_구독하면_이전_연결을_닫고_한_명으로_센다() {
        Long id = 진행중_경매();
        SseEmitter before = auctionFacade.subscribe(id, "tab-refresh").orElseThrow();
        SseEmitter after = auctionFacade.subscribe(id, "tab-refresh").orElseThrow();
        registered.add(new Registered(id, after));

        assertThat(registry.viewerCount(id)).isEqualTo(1);
        assertThat(registry.emitters(id)).containsExactly(after);
        // 닫힌 연결에는 더 보낼 수 없다
        assertThatThrownBy(() -> before.send("ping")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 탭_ID가_없거나_형식이_다르면_서로_다른_연결로_센다() {
        Long id = 진행중_경매();
        for (String clientId : new String[]{null, "", "bad id!"}) {
            registered.add(new Registered(id, auctionFacade.subscribe(id, clientId).orElseThrow()));
        }

        assertThat(registry.viewerCount(id)).isEqualTo(3);
    }

    @Test
    void 없는_경매는_구독할_수_없다() {
        assertThatThrownBy(() -> auctionFacade.subscribe(-1L, "tab-0001")).isInstanceOf(BusinessException.class);
        assertThat(registry.viewerCount(-1L)).isZero();
    }

    @Test
    void 시청자_수는_바뀌었을_때만_보낸다() throws InterruptedException {
        Long id = 진행중_경매();
        RecordingEmitter first = 구독자(id);
        RecordingEmitter second = 구독자(id);

        auctionStreamUseCase.broadcastViewers();
        assertThat(기다린다(v -> first.has("viewers") && second.has("viewers"))).isTrue();
        assertThat(first.events).anyMatch(text -> text.contains("\"count\":2"));

        first.events.clear();
        auctionStreamUseCase.broadcastViewers();   // 그대로 2명
        Thread.sleep(300);
        assertThat(first.events).isEmpty();
    }

    @Test
    void 경매가_끝나면_마지막_상태를_보낸다() throws InterruptedException {
        Long id = 진행중_경매();
        RecordingEmitter viewer = 구독자(id);
        jdbcTemplate.update("update auction set end_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), id);

        auctionFacade.failNoBidAuctions(LocalDateTime.now(), 1_000);

        assertThat(기다린다(v -> viewer.events.stream().anyMatch(t -> t.contains("\"status\":\"FAILED\"")))).isTrue();
    }
}
