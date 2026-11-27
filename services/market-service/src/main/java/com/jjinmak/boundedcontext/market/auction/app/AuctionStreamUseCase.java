package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.auction.domain.Auction;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionStreamBidDto;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionStreamPayloadDto;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionStreamViewersDto;
import com.jjinmak.boundedcontext.market.auction.out.AuctionDetailCache;
import com.jjinmak.boundedcontext.market.auction.out.AuctionStreamRegistry;
import com.jjinmak.boundedcontext.market.shared.domain.MarketMember;
import com.jjinmak.boundedcontext.market.shared.out.MarketMemberRepository;
import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

// 경매 상세 실시간 구독 (SSE, #557).
// 폴링은 시청자 수만큼 조회가 생긴다 → 상태가 바뀔 때만 서버가 보낸다 (조회가 입찰 수에 비례).
//
// 이벤트
// - auction : 상세의 auction + serverTime. 연결 직후 1번, 입찰·낙찰·유찰이 커밋된 뒤마다
// - bid     : 새 입찰 한 행 (닉네임 가림, isMine 없음)
// - viewers : 지금 구독 중인 연결 수. 연결 직후 1번, 이후 1초마다 바뀐 경매만 (입장마다 보내면 1,000명 입장에 100만 번)
// - 하트비트 : 20초마다 주석 줄. 프록시 타임아웃 방지 + 끊긴 연결 정리
//
// 전송은 전용 스레드에서 한다: 입찰 요청 스레드·스케줄러 스레드가 시청자 수만큼 쓰기를 기다리지 않게.
// 같은 내용은 JSON을 한 번만 만들어 모든 연결에 보낸다.
// 끝난 경매는 마지막 상태를 보낸 뒤 연결을 닫는다. 놓친 이벤트는 다시 보내지 않는다 (보내는 값이 전체 상태라 다음 이벤트·재연결로 맞춰짐)
@Slf4j
@Service
public class AuctionStreamUseCase {

    static final String EVENT_AUCTION = "auction";
    static final String EVENT_BID = "bid";
    static final String EVENT_VIEWERS = "viewers";
    // 같은 탭이 다시 연결해 이 연결이 바뀌었을 때 (#566). 탭을 복제해 탭 ID가 겹친 경우 프론트가 새 ID로 다시 연결한다
    static final String EVENT_REPLACED = "replaced";

    // 탭 ID: 프론트가 만든 UUID. 형식이 다르면 서버가 새로 만든다 (그 연결은 묶이지 않음)
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final AuctionReader auctionReader;
    private final AuctionDetailCache auctionDetailCache;
    private final AuctionStreamRegistry registry;
    private final MarketMemberRepository marketMemberRepository;
    private final ObjectMapper objectMapper;
    private final long timeoutMs;
    private final ExecutorService sender;

    // 경매별로 마지막에 보낸 시청자 수 (바뀐 경매에만 보내려고)
    private final Map<Long, Integer> sentViewers = new ConcurrentHashMap<>();

    public AuctionStreamUseCase(AuctionReader auctionReader,
                                AuctionDetailCache auctionDetailCache,
                                AuctionStreamRegistry registry,
                                MarketMemberRepository marketMemberRepository,
                                ObjectMapper objectMapper,
                                @Value("${market.auction.stream.timeout-ms:1800000}") long timeoutMs) {
        this.auctionReader = auctionReader;
        this.auctionDetailCache = auctionDetailCache;
        this.registry = registry;
        this.marketMemberRepository = marketMemberRepository;
        this.objectMapper = objectMapper;
        this.timeoutMs = timeoutMs;
        AtomicInteger seq = new AtomicInteger();
        // 대기열이 차면 가장 오래된 작업을 버린다: 경매 상태는 최신 것만 의미가 있다
        this.sender = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(10_000),
                runnable -> {
                    Thread thread = new Thread(runnable, "auction-stream-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
    }

    // 없는 경매는 BusinessException(AUCTION001). 연결 상한을 넘으면 empty (프론트는 폴링으로 대체)
    // clientId = 탭 ID (#566). 같은 탭이 다시 연결하면(새로고침) 이전 연결을 닫고 새 연결로 바꾼다 → 시청자 수가 늘지 않는다
    public Optional<SseEmitter> subscribe(Long auctionId, String clientId) {
        // 오픈 순간 시청자가 한꺼번에 들어와도 DB는 한 번만 (상세 캐시)
        Auction auction = auctionDetailCache.get(auctionId, auctionReader::get);

        SseEmitter emitter = new SseEmitter(timeoutMs);
        AuctionStreamRegistry.Registration registration = registry.add(auctionId, normalizeClientId(clientId), emitter);
        if (!registration.added()) {
            return Optional.empty();
        }
        emitter.onCompletion(() -> registry.remove(auctionId, emitter));
        emitter.onTimeout(() -> registry.remove(auctionId, emitter));
        emitter.onError(error -> registry.remove(auctionId, emitter));
        if (registration.replaced() != null) {
            close(registration.replaced());   // 새로고침이면 이미 끊긴 연결이라 보내기는 조용히 실패한다
        }

        // 첫 이벤트를 바로 보내야 응답이 시작된다 (상세 조회와 구독 사이에 놓친 변경도 이걸로 맞춰짐)
        boolean sent = send(auctionId, emitter, EVENT_AUCTION, json(AuctionStreamPayloadDto.of(auction, LocalDateTime.now())))
                && send(auctionId, emitter, EVENT_VIEWERS, json(new AuctionStreamViewersDto(registry.viewerCount(auctionId))));
        if (sent && !auction.isInProgress()) {
            emitter.complete();   // 끝난 경매: 마지막 상태만 보내고 닫는다
        }
        return Optional.of(emitter);
    }

    // 입찰·낙찰·유찰이 커밋된 뒤 (AuctionStreamEventListener)
    public void pushAuction(Long auctionId) {
        if (registry.viewerCount(auctionId) == 0) {
            return;   // 보는 사람이 없으면 DB도 읽지 않는다
        }
        sender.execute(() -> {
            try {
                Auction auction = auctionReader.get(auctionId);   // 캐시를 거치지 않고 커밋된 최신 상태
                broadcast(auctionId, EVENT_AUCTION, json(AuctionStreamPayloadDto.of(auction, LocalDateTime.now())));
                if (!auction.isInProgress()) {
                    registry.emitters(auctionId).forEach(SseEmitter::complete);
                }
            } catch (Exception e) {
                log.warn("경매 상태 전송 실패 auctionId={}", auctionId, e);
            }
        });
    }

    public void pushBid(AuctionBidSucceedEvent event) {
        if (registry.viewerCount(event.auctionId()) == 0) {
            return;
        }
        sender.execute(() -> {
            try {
                String nickname = marketMemberRepository.findById(event.bidderId())
                        .map(MarketMember::getNickname)
                        .orElse(null);
                AuctionStreamBidDto bid = new AuctionStreamBidDto(event.bidId(),
                        AuctionBidHistoryUseCase.maskNickname(nickname), event.bidPrice(), event.bidAt());
                broadcast(event.auctionId(), EVENT_BID, json(bid));
            } catch (Exception e) {
                log.warn("입찰 전송 실패 auctionId={}", event.auctionId(), e);
            }
        });
    }

    // 1초마다 (AuctionStreamScheduler): 시청자 수가 바뀐 경매에만 보낸다
    public void broadcastViewers() {
        sentViewers.keySet().retainAll(registry.auctionIds());
        for (Long auctionId : registry.auctionIds()) {
            int count = registry.viewerCount(auctionId);
            Integer previous = sentViewers.put(auctionId, count);
            if (previous == null || previous != count) {
                String payload = json(new AuctionStreamViewersDto(count));
                sender.execute(() -> broadcast(auctionId, EVENT_VIEWERS, payload));
            }
        }
    }

    // 20초마다 (AuctionStreamScheduler): 연결 유지 + 끊긴 연결 정리 (보내기가 실패하면 지운다)
    public void heartbeat() {
        for (Long auctionId : registry.auctionIds()) {
            sender.execute(() -> {
                for (SseEmitter emitter : registry.emitters(auctionId)) {
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                    } catch (IOException | IllegalStateException e) {
                        drop(auctionId, emitter);
                    }
                }
            });
        }
    }

    private void broadcast(Long auctionId, String event, String payload) {
        List<SseEmitter> emitters = registry.emitters(auctionId);
        for (SseEmitter emitter : emitters) {
            send(auctionId, emitter, event, payload);
        }
    }

    private boolean send(Long auctionId, SseEmitter emitter, String event, String payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(payload));
            return true;
        } catch (IOException | IllegalStateException e) {
            drop(auctionId, emitter);   // 끊긴 연결
            return false;
        }
    }

    static String normalizeClientId(String clientId) {
        return clientId != null && CLIENT_ID.matcher(clientId).matches() ? clientId : UUID.randomUUID().toString();
    }

    // 바뀐 이전 연결: 알리고 닫는다. 보관소에서는 이미 빠졌다
    private void close(SseEmitter replaced) {
        try {
            replaced.send(SseEmitter.event().name(EVENT_REPLACED).data("{}"));
        } catch (IOException | IllegalStateException ignored) {
            // 끊긴 연결
        }
        try {
            replaced.complete();
        } catch (Exception ignored) {
            // 이미 닫힌 연결
        }
    }

    private void drop(Long auctionId, SseEmitter emitter) {
        registry.remove(auctionId, emitter);
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 이미 닫힌 연결
        }
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
