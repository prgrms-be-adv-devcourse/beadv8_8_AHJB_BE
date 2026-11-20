-- 원장: 정산금 출금 유형 허용 (판매자 정산금 → 등록 계좌, 명목상)
-- 출금 건(withdrawal)의 account 는 처음부터 DEPOSIT / SETTLEMENT 를 허용하므로 그대로 둔다
ALTER TABLE wallet_transactions
    DROP CONSTRAINT ck_wallet_tx_type;
ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_tx_type CHECK (type IN ('CHARGE', 'CHARGE_CANCEL', 'WITHDRAWAL', 'SETTLEMENT_WITHDRAWAL',
                                                     'HOLD_PAY', 'HOLD_RECEIVE',
                                                     'SETTLEMENT_PAY', 'SETTLEMENT_RECEIVE',
                                                     'FEE_PAY', 'FEE_RECEIVE',
                                                     'REFUND_PAY', 'REFUND_RECEIVE',
                                                     'POINT_EARN',
                                                     'POINT_HOLD_PAY', 'POINT_HOLD_RECEIVE',
                                                     'POINT_REFUND_PAY', 'POINT_REFUND_RECEIVE',
                                                     'PG_HOLD_RECEIVE', 'PG_REFUND_PAY',
                                                     'REVERSAL'));
