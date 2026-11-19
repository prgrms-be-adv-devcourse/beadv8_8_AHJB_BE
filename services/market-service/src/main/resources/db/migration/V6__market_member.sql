-- market 회원 통합
-- product_member, auction_member, review_member는 모두 회원 가입 이벤트로 복제한 (id, 닉네임)이라 내용이 같다.
-- market_member 하나로 합치고, 외래키를 옮긴 뒤 옛 테이블을 지운다.
-- (테스트용 H2도 같은 스크립트를 실행하므로 표준 SQL만 쓴다)

CREATE TABLE market_member (
    id         bigint       NOT NULL,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    nickname   varchar(255) NOT NULL,
    CONSTRAINT pk_market_member PRIMARY KEY (id)
);

-- 1) 데이터 합치기: 상품 → 경매 → 후기 순서로, 아직 없는 회원만 넣는다
INSERT INTO market_member (id, created_at, updated_at, nickname)
SELECT id, created_at, updated_at, nickname FROM product_member;

INSERT INTO market_member (id, created_at, updated_at, nickname)
SELECT a.id, a.created_at, a.updated_at, a.nickname FROM auction_member a
WHERE NOT EXISTS (SELECT 1 FROM market_member m WHERE m.id = a.id);

INSERT INTO market_member (id, created_at, updated_at, nickname)
SELECT r.id, r.created_at, r.updated_at, r.nickname FROM review_member r
WHERE NOT EXISTS (SELECT 1 FROM market_member m WHERE m.id = r.id);

-- 2) 외래키 옮기기 (이름은 그대로)
ALTER TABLE product DROP CONSTRAINT fk_product_seller;
ALTER TABLE product ADD CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES market_member (id);

ALTER TABLE product_image DROP CONSTRAINT fk_product_image_uploader;
ALTER TABLE product_image ADD CONSTRAINT fk_product_image_uploader FOREIGN KEY (uploader_id) REFERENCES market_member (id);

ALTER TABLE product_wish DROP CONSTRAINT fk_product_wish_member;
ALTER TABLE product_wish ADD CONSTRAINT fk_product_wish_member FOREIGN KEY (member_id) REFERENCES market_member (id);

ALTER TABLE product_qna DROP CONSTRAINT fk_product_qna_writer;
ALTER TABLE product_qna ADD CONSTRAINT fk_product_qna_writer FOREIGN KEY (writer_id) REFERENCES market_member (id);

ALTER TABLE reviewable_order DROP CONSTRAINT fk_reviewable_order_buyer;
ALTER TABLE reviewable_order ADD CONSTRAINT fk_reviewable_order_buyer FOREIGN KEY (buyer_id) REFERENCES market_member (id);

ALTER TABLE reviewable_order DROP CONSTRAINT fk_reviewable_order_seller;
ALTER TABLE reviewable_order ADD CONSTRAINT fk_reviewable_order_seller FOREIGN KEY (seller_id) REFERENCES market_member (id);

ALTER TABLE review DROP CONSTRAINT fk_review_writer;
ALTER TABLE review ADD CONSTRAINT fk_review_writer FOREIGN KEY (writer_id) REFERENCES market_member (id);

ALTER TABLE review DROP CONSTRAINT fk_review_target;
ALTER TABLE review ADD CONSTRAINT fk_review_target FOREIGN KEY (target_id) REFERENCES market_member (id);

-- 3) 옛 테이블 삭제
DROP TABLE product_member;
DROP TABLE auction_member;
DROP TABLE review_member;
