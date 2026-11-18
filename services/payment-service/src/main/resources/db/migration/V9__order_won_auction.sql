CREATE TABLE order_won_auction (
                                   id          bigint       NOT NULL,
                                   created_at  timestamp(6) NOT NULL,
                                   updated_at  timestamp(6) NOT NULL,
                                   CONSTRAINT pk_order_won_auction PRIMARY KEY (id)
);

INSERT INTO order_won_auction (id, created_at, updated_at)
SELECT auction_id, now(), now()
FROM (SELECT auction_id FROM cart_item UNION SELECT auction_id FROM orders) won;