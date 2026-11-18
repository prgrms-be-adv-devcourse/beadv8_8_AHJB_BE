-- 지갑 포인트 주머니
-- 포인트는 적립으로 들어오고 결제로 나간다. 1포인트 = 1원이며 출금할 수 없다 (ck_withdrawal_account 는 그대로 둔다)
ALTER TABLE wallet
    ADD COLUMN point_balance bigint NOT NULL DEFAULT 0;

-- 원장: 포인트 주머니와 포인트 거래 유형 허용
ALTER TABLE wallet_transactions
    DROP CONSTRAINT ck_wallet_tx_account;
ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_tx_account CHECK (account IN ('DEPOSIT', 'SETTLEMENT', 'POINT'));

ALTER TABLE wallet_transactions
    DROP CONSTRAINT ck_wallet_tx_type;
ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_tx_type CHECK (type IN ('CHARGE', 'CHARGE_CANCEL', 'WITHDRAWAL',
                                                     'HOLD_PAY', 'HOLD_RECEIVE',
                                                     'SETTLEMENT_PAY', 'SETTLEMENT_RECEIVE',
                                                     'FEE_PAY', 'FEE_RECEIVE',
                                                     'REFUND_PAY', 'REFUND_RECEIVE',
                                                     'POINT_EARN',
                                                     'POINT_HOLD_PAY', 'POINT_HOLD_RECEIVE',
                                                     'POINT_REFUND_PAY', 'POINT_REFUND_RECEIVE',
                                                     'REVERSAL'));
