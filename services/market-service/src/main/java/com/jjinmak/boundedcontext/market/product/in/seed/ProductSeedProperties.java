package com.jjinmak.boundedcontext.market.product.in.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 추천 확인용 상품 자동 등록 설정. 운영에서는 환경 변수로 켠다. (MARKET_SEED_ENABLED, MARKET_SEED_SELLER_ID)
// 등록 간격은 스케줄러의 market.seed.interval(ms, 기본 5분)로 조절한다.
@ConfigurationProperties(prefix = "market.seed")
public record ProductSeedProperties(
        @DefaultValue("false")
        boolean enabled,

        Long sellerId,          // 상품을 등록할 판매자 회원 id (market_member에 있어야 함)

        @DefaultValue("300")
        int maxCount            // 이 판매자가 등록한 시드 상품이 이만큼 쌓이면 멈춘다.
) {
}
