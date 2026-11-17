package com.jjinmak.ai.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.jjinmak.ai.recommend.exception.RecommendErrorCode.SEARCH_TOO_MANY_REQUESTS;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendSearchRateLimiterTest {

    final RecommendSearchRateLimiter rateLimiter = new RecommendSearchRateLimiter();

    @Test
    @DisplayName("한 회원은 5번까지 통과하고 6번째는 recommend005로 거절된다")
    void check_rejectsSixthCall() {
        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> rateLimiter.check(1L)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> rateLimiter.check(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SEARCH_TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("회원마다 따로 센다")
    void check_countsEachMemberSeparately() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.check(1L);
        }

        assertThatCode(() -> rateLimiter.check(2L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> rateLimiter.check(1L)).isInstanceOf(BusinessException.class);
    }
}
