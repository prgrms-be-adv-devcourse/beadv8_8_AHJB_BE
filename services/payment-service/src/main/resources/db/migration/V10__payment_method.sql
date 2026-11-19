-- 결제수단 선택 · 직접 결제 · 원결제수단 환불
-- (테스트의 H2 에서도 실행되므로 ALTER TABLE 은 한 문장에 한 가지만 바꾼다)

-- payment: 고른 결제수단과 결제 금액 구성 (포인트 + 예치금 + 직접 결제 = 총액)
ALTER TABLE payment
    ADD COLUMN pay_method varchar(20) NOT NULL DEFAULT 'DEPOSIT';
ALTER TABLE payment
    ADD COLUMN point_amount bigint NOT NULL DEFAULT 0;
ALTER TABLE payment
    ADD COLUMN deposit_amount bigint NOT NULL DEFAULT 0;
ALTER TABLE payment
    ADD COLUMN pg_amount bigint NOT NULL DEFAULT 0;
ALTER TABLE payment
    ADD COLUMN pg_method varchar(30);

-- 지금까지의 결제는 전액 예치금 결제다
UPDATE payment
SET deposit_amount = total_price;

ALTER TABLE payment
    ADD CONSTRAINT ck_payment_pay_method CHECK (pay_method IN ('DEPOSIT', 'TRANSFER', 'CARD'));
ALTER TABLE payment
    ADD CONSTRAINT ck_payment_amounts CHECK (point_amount >= 0 AND deposit_amount >= 0 AND pg_amount >= 0
        AND point_amount + deposit_amount + pg_amount = total_price);

-- holding: 주문별 결제 금액 구성 (포인트 + 예치금 + 직접 결제 = 홀딩 금액). 환불은 이 구성대로 돌려준다
ALTER TABLE holding
    ADD COLUMN point_amount bigint NOT NULL DEFAULT 0;
ALTER TABLE holding
    ADD COLUMN deposit_amount bigint NOT NULL DEFAULT 0;
ALTER TABLE holding
    ADD COLUMN pg_amount bigint NOT NULL DEFAULT 0;

-- 지금까지의 홀딩은 전액 예치금으로 낸 것이다
UPDATE holding
SET deposit_amount = amount;

ALTER TABLE holding
    ADD CONSTRAINT ck_holding_amounts CHECK (point_amount >= 0 AND deposit_amount >= 0 AND pg_amount >= 0
        AND point_amount + deposit_amount + pg_amount = amount);

-- charge: 직접 결제 용도, 결제창을 여는 결제수단, 취소 누계 (직접 결제의 부분 취소 추적)
ALTER TABLE charge
    ADD COLUMN requested_method varchar(20);
ALTER TABLE charge
    ADD COLUMN canceled_amount bigint NOT NULL DEFAULT 0;

-- 지금까지의 충전은 모두 계좌이체로 여는 예치금 충전이고, 취소된 건은 전액 취소다
UPDATE charge
SET requested_method = 'TRANSFER';
UPDATE charge
SET canceled_amount = amount
WHERE status = 'CANCELED';

ALTER TABLE charge
    DROP CONSTRAINT ck_charge_purpose;
ALTER TABLE charge
    ADD CONSTRAINT ck_charge_purpose CHECK (purpose IN ('MANUAL', 'FOR_PAYMENT', 'DIRECT_PAYMENT'));
ALTER TABLE charge
    ADD CONSTRAINT ck_charge_requested_method CHECK (requested_method IN ('TRANSFER', 'CARD'));
ALTER TABLE charge
    ADD CONSTRAINT ck_charge_canceled_amount CHECK (canceled_amount >= 0 AND canceled_amount <= amount);

-- 원장: 직접 결제 유형 허용 (토스 → ESCROW, ESCROW → 토스)
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
                                                     'PG_HOLD_RECEIVE', 'PG_REFUND_PAY',
                                                     'REVERSAL'));
