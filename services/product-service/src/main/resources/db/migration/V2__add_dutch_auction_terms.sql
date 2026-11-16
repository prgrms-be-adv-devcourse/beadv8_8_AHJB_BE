-- 네덜란드식(DUTCH) 경매 조건 추가
-- 기존 상품은 모두 영국식(ENGLISH)으로 채운다.
ALTER TABLE product ADD COLUMN auction_type varchar(255) NOT NULL DEFAULT 'ENGLISH';

-- 네덜란드식 전용: 최저가, 하락률(시작가 기준 %), 하락 주기(초)
ALTER TABLE product ADD COLUMN floor_price bigint;
ALTER TABLE product ADD COLUMN drop_rate_percent integer;
ALTER TABLE product ADD COLUMN drop_interval_sec integer;

-- 네덜란드식은 진행 기간이 없다. (가격이 최저가까지 내려가면 끝난다)
ALTER TABLE product ALTER COLUMN duration DROP NOT NULL;

ALTER TABLE product ADD CONSTRAINT ck_product_auction_type
    CHECK (auction_type IN ('ENGLISH', 'DUTCH'));
ALTER TABLE product ADD CONSTRAINT ck_product_drop_rate_percent
    CHECK (drop_rate_percent IN (1, 2, 3));
ALTER TABLE product ADD CONSTRAINT ck_product_drop_interval_sec
    CHECK (drop_interval_sec IN (30, 60, 90, 120));

-- 경매 방식별로 필요한 조건만 채워져 있어야 한다.
-- 영국식: 기간 필수, 네덜란드 조건 없음 / 네덜란드식: 네덜란드 조건 필수, 즉시낙찰가·기간·종료 시각 없음
ALTER TABLE product ADD CONSTRAINT ck_product_auction_terms CHECK (
    (auction_type = 'ENGLISH'
        AND duration IS NOT NULL
        AND floor_price IS NULL AND drop_rate_percent IS NULL AND drop_interval_sec IS NULL)
    OR
    (auction_type = 'DUTCH'
        AND duration IS NULL AND instant_win_price IS NULL AND custom_end_at IS NULL
        AND floor_price IS NOT NULL AND drop_rate_percent IS NOT NULL AND drop_interval_sec IS NOT NULL)
);
