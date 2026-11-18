package com.jjinmak.back.boundedContext.payment.app.dto;

/** 홀딩 조회 시 내가 어느 쪽인가. BUYER = 내 돈이 묶여 있음, SELLER = 내가 받을 돈이 묶여 있음 */
public enum HoldingRole {
    BUYER,
    SELLER
}
