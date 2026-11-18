-- 경매 회차 (재등록)
-- 같은 상품을 다시 경매에 올리면 회차가 1씩 늘고, 회차마다 경매가 하나씩 생긴다.
-- 기존 데이터는 모두 1회차다.

ALTER TABLE auction ADD COLUMN round integer NOT NULL DEFAULT 1;

-- 같은 상품의 같은 회차 경매는 하나만 (동시에 두 번 생성 요청이 와도 DB가 막는다)
ALTER TABLE auction ADD CONSTRAINT uk_auction_product_round UNIQUE (product_id, round);

-- 상품의 현재 회차 (current_auction_id가 가리키는 경매의 회차)
ALTER TABLE product ADD COLUMN auction_round integer NOT NULL DEFAULT 1;
