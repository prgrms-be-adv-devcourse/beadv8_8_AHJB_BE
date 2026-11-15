package com.jjinmak.member.domain;

import lombok.Getter;

@Getter
public final class Authority {

    public static final String ROLE_BUYER = "ROLE_BUYER";
    public static final String ROLE_SELLER = "ROLE_SELLER";
    // 관리자 (네덜란드식 경매 등록 등). 가입 후 DB에서 직접 지정한다.
    public static final String ROLE_ADMIN = "ROLE_ADMIN";


    public static final String PERM_TRADE = "PERM_TRADE";
    public static final String PERM_SELL = "PERM_SELL";

    private Authority() {}
}
