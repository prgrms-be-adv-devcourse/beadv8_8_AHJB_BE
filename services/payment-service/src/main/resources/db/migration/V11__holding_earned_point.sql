-- 구매 확정 시 포인트 적립
-- holding: 이 주문의 구매 확정으로 구매자에게 적립된 포인트. 구매 확정 전이거나 적립 대상 금액이 없으면 0
ALTER TABLE holding
    ADD COLUMN earned_point bigint NOT NULL DEFAULT 0;

ALTER TABLE holding
    ADD CONSTRAINT ck_holding_earned_point CHECK (earned_point >= 0);
