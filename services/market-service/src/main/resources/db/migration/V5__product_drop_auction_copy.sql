-- 1) 상품 회차 컬럼 정리
-- V4에서 추가한 auction_round는 V1부터 있던 round와 같은 뜻이다. 재등록 회차가 auction_round에만 쌓였으므로 round로 옮기고 지운다.
UPDATE product SET round = auction_round WHERE round <> auction_round;
ALTER TABLE product DROP COLUMN auction_round;

-- 2) 상품의 경매 상태 복사본 삭제
-- 경매 상태·결과는 auction 테이블에만 둔다. 상품 화면은 경매 상태를 읽어서 보여 준다.
-- (테스트용 H2도 같은 스크립트를 실행하므로 제약을 먼저 지우고 컬럼을 하나씩 지운다)
ALTER TABLE product DROP CONSTRAINT IF EXISTS ck_product_auction_status;
ALTER TABLE product DROP CONSTRAINT IF EXISTS ck_product_auction_failure_reason;
ALTER TABLE product DROP COLUMN auction_status;
ALTER TABLE product DROP COLUMN auction_end_at;
ALTER TABLE product DROP COLUMN auction_failure_reason;
