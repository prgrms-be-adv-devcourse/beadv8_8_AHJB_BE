package com.jjinmak.payment.domain.enums;


/**
 * CHARGE : chargeId
 * PG_TRANSACTION : pgOrderId
 * WALLET : walletId
 * HOLDING : orderId
 * LEDGER_GROUP : transactionGroupId
 * */
public enum MismatchTargetType {
    CHARGE,
    PG_TRANSACTION,
    WALLET,
    HOLDING,
    LEDGER_GROUP
}
