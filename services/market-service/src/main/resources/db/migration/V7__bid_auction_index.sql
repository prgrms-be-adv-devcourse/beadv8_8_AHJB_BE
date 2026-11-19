-- 입찰 내역 조회 인덱스 (#515)
-- GET /api/v1/auctions/{auctionId}/bids 는 "이 경매의 입찰을 최신순으로 N건"을 읽는다.
-- 기존 인덱스는 (bidder_id, auction_id)뿐이라 PK를 거꾸로 훑고(입찰 100만 건에서 89ms),
-- 페이지 count는 테이블 전체를 스캔했다(44ms) → 초당 약 38건이 한계, p95 21초.
-- (auction_id, id DESC)면 해당 경매 범위만 최신순으로 읽고 size+1건에서 멈춘다.
-- 운영 테이블이 커진 뒤라면 CREATE INDEX CONCURRENTLY(트랜잭션 밖 실행)로 바꾼다. 지금 데이터는 작아 일반 생성으로 충분하다.
-- (테스트용 H2도 같은 스크립트를 실행하므로 표준 SQL만 쓴다)

CREATE INDEX idx_bid_auction_id ON bid (auction_id, id DESC);
