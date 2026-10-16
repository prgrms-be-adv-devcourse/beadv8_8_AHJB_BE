package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.WALLET_CONCURRENT_MODIFICATION;

/**
 * 사용자 지갑은 낙관적 락(@Version)이라 동시에 잔액이 바뀌면 OptimisticLockingFailureException 이 난다.
 * 트랜잭션 "전체"를 다시 실행해야 하므로, @Transactional 메서드 바깥(파사드)에서 이걸로 감싼다.
 * (옛 WalletPortAdapter.withRetry 를 옮겨온 것)
 */
@Slf4j
@Component
public class WalletRetrySupport {

    private static final int MAX_ATTEMPTS = 3;

    public <T> T withRetry(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (OptimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("지갑 낙관적 락 충돌, 재시도 소진 ({}회)", attempt, e);
                    throw new BusinessException(WALLET_CONCURRENT_MODIFICATION);
                }
                log.debug("지갑 낙관적 락 충돌, 재시도 {}/{}", attempt, MAX_ATTEMPTS);
            }
        }
    }

    /** 반환값이 없는 단계용. (Supplier 오버로드와 람다 모호성을 피하려고 이름을 다르게 둔다) */
    public void withRetryRun(Runnable action) {
        withRetry(() -> {
            action.run();
            return null;
        });
    }
}
