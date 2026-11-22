CREATE INDEX idx_orders_winner_created ON orders (winner_id, created_at);
CREATE INDEX idx_orders_seller_created ON orders (seller_id, created_at);
CREATE INDEX idx_orders_group ON orders (group_id);
CREATE INDEX idx_order_group_winner ON order_group (winner_id, life_time);
CREATE INDEX idx_cart_item_winner ON cart_item (winner_id);