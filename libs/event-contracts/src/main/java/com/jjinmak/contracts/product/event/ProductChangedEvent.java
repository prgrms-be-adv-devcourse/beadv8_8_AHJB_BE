package com.jjinmak.contracts.product.event;

import java.time.LocalDateTime;

// 상품이 등록·수정·삭제될 때마다 발행한다. (검색 인덱스 동기화용)
// - 바뀐 값만이 아니라 상품 전체 정보를 담는다. 받는 쪽은 이 값으로 덮어쓰면 된다.
// - Kafka 키는 productId라서 같은 상품의 이벤트는 순서대로 도착한다.
// - version은 상품 엔티티의 @Version 값이다. 받는 쪽은 저장된 값보다 작으면 오래된 이벤트로 보고 무시한다.
//   (이미지만 바꾼 수정은 버전이 그대로일 수 있으므로, 같은 버전은 반영한다)
// - DELETED도 마지막 상품 정보를 함께 담는다.
public record ProductChangedEvent(
        Long productId,
        ProductChangeType changeType,
        Long version,
        Long sellerId,
        String productName,
        String category,                // ProductCategory.name()
        String manufacturer,            // Manufacturer.name()
        String manufacturerName,        // 한글 이름 ("닌텐도"). 기타(ETC)면 판매자가 직접 입력한 이름
        Long startPrice,
        Long instantWinPrice,           // 선택
        LocalDateTime auctionScheduledAt,   // 경매 시작 예정 시각 (곧 시작 탭, 시작 임박순 정렬)
        String thumbnailUrl,            // 이미지가 없으면 null
        LocalDateTime occurredAt
) {}
