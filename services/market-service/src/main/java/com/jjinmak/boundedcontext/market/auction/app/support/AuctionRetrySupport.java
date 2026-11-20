package com.jjinmak.boundedcontext.market.auction.app.support;

import com.jjinmak.boundedcontext.market.auction.exception.AuctionErrorCode;
import com.jjinmak.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 영국식 입찰·즉시낙찰의 낙관적 락 충돌 재시도.
 *
 * <p>낙관적 락은 금액과 무관하게 먼저 커밋한 쪽만 남긴다. 그래서 더 높은 금액의 유효한 입찰도
 * 같은 version을 읽었다는 이유만으로 AUCTION008을 받는다(CT-09 유효 입찰 거부 48~74%).
 * 충돌이면 <b>새 트랜잭션</b>에서 최신 경매를 다시 읽고 검증부터 다시 실행한다.
 *
 * <ul>
 *   <li>트랜잭션 밖(@Transactional 파사드 바깥)에서만 호출한다. 같은 트랜잭션 안에서 다시 시도하면
 *       영속성 컨텍스트에 남은 옛 version 때문에 또 충돌한다.</li>
 *   <li>업무 예외(금액 부족·이미 최고 입찰자·종료 등)는 재시도하지 않는다. 더 높은 입찰이 먼저 들어갔으면
 *       재검증에서 "금액 부족"으로 끝나므로 업무 규칙이 재시도 사슬을 끊는다.</li>
 *   <li>최대 시도: 입찰 3회, 즉시낙찰 5회(경매를 끝내는 동작이라 입찰 수십 건과 겹쳐도 실패하면 비용이 크다).
 *       시도 사이 대기는 5~20ms × 시도 횟수 무작위(트랜잭션 밖이라 커넥션을 잡지 않는다).</li>
 *   <li>하향식 구매에는 쓰지 않는다. 다시 시도해도 결과는 AUCTION014(이미 판매)라 충돌 자체가 정당한 거부다.</li>
 * </ul>
 *
 * <p>지표: {@code auction.retry.attempts}(시도 수 합) ÷ {@code auction.retry.requests}(요청 수) = 요청당 평균 시도 수.
 * 카운터는 생성 시점에 0으로 미리 등록한다. 처음 쓰일 때 만들면 Prometheus increase()가 첫 증가분을 놓친다.
 */
@Slf4j
@Component
public class AuctionRetrySupport {

    static final long MIN_BACKOFF_MS = 5;
    static final long MAX_BACKOFF_MS = 20;

    public enum Operation {
        BID("bid", 3), INSTANT_WIN("instant_win", 5);

        private final String tag;
        private final int maxAttempts;

        Operation(String tag, int maxAttempts) {
            this.tag = tag;
            this.maxAttempts = maxAttempts;
        }

        int maxAttempts() {
            return maxAttempts;
        }
    }

    private static final String[] RESULTS = {"success", "rejected", "exhausted"};

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final MeterRegistry meterRegistry;
    private final Sleeper sleeper;

    @Autowired
    public AuctionRetrySupport(MeterRegistry meterRegistry) {
        this(meterRegistry, Thread::sleep);
    }

    AuctionRetrySupport(MeterRegistry meterRegistry, Sleeper sleeper) {
        this.meterRegistry = meterRegistry;
        this.sleeper = sleeper;
        for (Operation operation : Operation.values()) {
            meterRegistry.counter("auction.retry.attempts", "operation", operation.tag);
            for (String result : RESULTS) {
                meterRegistry.counter("auction.retry.requests", "operation", operation.tag, "result", result);
            }
        }
    }

    public <T> T execute(Operation operation, Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("AuctionRetrySupport는 트랜잭션 밖에서 호출해야 합니다");
        }
        for (int attempt = 1; ; attempt++) {
            try {
                T result = action.get();
                record(operation, attempt, "success");
                return result;
            } catch (BusinessException e) {
                record(operation, attempt, "rejected");
                throw e;
            } catch (OptimisticLockingFailureException e) {
                if (attempt >= operation.maxAttempts()) {
                    record(operation, attempt, "exhausted");
                    log.info("경매 낙관적 락 충돌, 재시도 소진 ({} {}회)", operation, attempt);
                    throw new BusinessException(AuctionErrorCode.BID_CONFLICT);
                }
                log.debug("경매 낙관적 락 충돌, 재시도 {} {}/{}", operation, attempt, operation.maxAttempts());
                backoff(operation, attempt);
            }
        }
    }

    private void backoff(Operation operation, int attempt) {
        long delay = ThreadLocalRandom.current().nextLong(MIN_BACKOFF_MS, MAX_BACKOFF_MS + 1) * attempt;
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            record(operation, attempt, "exhausted");
            throw new BusinessException(AuctionErrorCode.BID_CONFLICT);
        }
    }

    private void record(Operation operation, int attempts, String result) {
        meterRegistry.counter("auction.retry.attempts", "operation", operation.tag).increment(attempts);
        meterRegistry.counter("auction.retry.requests", "operation", operation.tag, "result", result).increment();
    }
}
