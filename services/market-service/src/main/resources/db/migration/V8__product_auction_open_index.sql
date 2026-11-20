-- 경매 열기 대상 조회 인덱스 (#516)
-- 경매 열기 스케줄러 주기를 60초 → 1초로 줄인다(시작 지연 0~60초 → 0~1초).
-- 매초 실행하는 대상 조회는 "아직 경매를 요청하지 않았고(auction_requested_at IS NULL) 시작 예정 시각이 지난 상품"이다.
-- 지금은 이 조건에 맞는 인덱스가 없어 상품 테이블 전체를 읽는다. 상품이 쌓여도 대기 중인 상품 범위만 읽도록 인덱스를 둔다.
-- (요청 전 상품은 전체 중 일부라 이 범위는 작다. 테스트용 H2도 같은 스크립트를 실행하므로 부분 인덱스 대신 표준 복합 인덱스를 쓴다)

CREATE INDEX idx_product_auction_open ON product (auction_requested_at, auction_scheduled_at);
