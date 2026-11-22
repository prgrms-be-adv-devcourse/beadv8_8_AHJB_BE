DELETE FROM orders
WHERE group_id IN (
    SELECT group_id FROM orders
    WHERE state = 'WAITING'
      AND auction_id IN (SELECT auction_id FROM orders GROUP BY auction_id HAVING COUNT(*) > 1)
);

DELETE FROM order_group
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE orders.group_id = order_group.id);

ALTER TABLE orders ADD CONSTRAINT uk_orders_auction_id UNIQUE (auction_id);