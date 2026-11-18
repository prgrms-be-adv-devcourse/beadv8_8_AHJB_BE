package com.jjinmak.contracts.product.event;

import java.time.LocalDateTime;

// 회원이 상품을 찜하거나 찜을 취소할 때 발행한다. (추천 관심도, 찜한 상품 알림용)
// - 등록·취소를 한 토픽으로 보낸다. Kafka 키는 productId라서 같은 상품의 찜·취소는 순서대로 도착한다.
//   (토픽을 나누면 "찜 → 취소"가 "취소 → 찜" 순서로 도착할 수 있다)
// - 받는 쪽은 같은 회원·상품의 이벤트 중 occurredAt이 더 늦은 것을 최종 상태로 보면 된다.
public record ProductWishChangedEvent(
        Long productId,
        Long memberId,                      // 찜한(취소한) 회원
        ProductWishChangeType changeType,
        Long auctionId,                     // 지금 연결된 경매 id. 경매 시작 전이면 null
        LocalDateTime occurredAt
) {}
