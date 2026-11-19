package com.jjinmak.boundedcontext.chat.domain;

/** 메시지가 차단된 이유. 로그·추후 차단 기록용 (사용자에겐 공통 안내 문구만 보여준다) */
public enum ChatBlockReason {
    PHONE_NUMBER,
    BANK_ACCOUNT,
    EXTERNAL_CHANNEL,
    CASH_TRADE
}