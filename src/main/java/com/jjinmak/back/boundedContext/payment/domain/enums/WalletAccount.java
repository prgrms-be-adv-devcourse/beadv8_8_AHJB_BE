package com.jjinmak.back.boundedContext.payment.domain.enums;

/**
 * 지갑 안의 잔액 구분. 한 지갑은 두 주머니를 가진다.
 *
 * - DEPOSIT    : 예치금. 충전·환불로 들어오고 결제·충전 취소로 나간다
 * - SETTLEMENT : 정산금. 구매 확정으로 판매자가 받은 판매 대금. 예치금과 섞이지 않으며
 *                결제·충전 취소에 쓰이지 않는다 (계좌 송금이 가능해지면 정산 계좌로 송금)
 */
public enum WalletAccount {
    DEPOSIT,
    SETTLEMENT
}
