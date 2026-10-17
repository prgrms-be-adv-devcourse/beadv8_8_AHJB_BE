package com.jjinmak.back.boundedContext.auction.domain;

import com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode;
import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class BidIncrementTest {

    // 문자열 안에서는 숫자 밑줄(10_000)을 쓸 수 없다
    @ParameterizedTest(name = "현재가 {0}원 → 단위 {1}원")
    @CsvSource({
            "0, 500",
            "9999, 500",
            "10000, 1000",
            "99999, 1000",
            "100000, 5000",
            "499999, 5000",
            "500000, 10000",
            "999999, 10000",
            "1000000, 50000",
            "4999999, 50000",
            "5000000, 100000",
            "9223372036854775807, 100000"   // Long 최댓값
    })
    void 현재_가격_구간에_맞는_입찰_단위를_반환한다(long currentPrice, long expectedUnit) {
        assertThat(BidIncrement.unitOf(currentPrice)).isEqualTo(expectedUnit);
    }

    @ParameterizedTest(name = "가격 {0}원")
    @ValueSource(longs = {-1, -10_000, Long.MIN_VALUE})
    void 음수_가격은_INVALID_PRICE_예외가_발생한다(long negativePrice) {
        assertThatThrownBy(() -> BidIncrement.unitOf(negativePrice))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.INVALID_PRICE);
    }
}