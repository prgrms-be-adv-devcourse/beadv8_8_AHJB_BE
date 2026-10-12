package com.jjinmak.back.boundedContext.wallet.domain.enums;

/**
 * 지갑 종류.
 * USER   : 사용자(구매자·판매자) 지갑. 사용자마다 1개.
 * ESCROW : 거래 중 묶여 있는 대금을 모아 두는 시스템 지갑. 전체 1개.
 * FEE    : 플랫폼 수수료 수익이 쌓이는 시스템 지갑. 전체 1개.
 */

public enum WalletType {
    USER,
    ESCROW,
    FEE
}
