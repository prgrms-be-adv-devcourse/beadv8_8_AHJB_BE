package com.jjinmak.back.boundedContext.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public final class Authority {

    public static final String ROLE_BUYER = "ROLE_BUYER";
    public static final String ROLE_SELLER = "ROLE_SELLER";


    public static final String PERM_TRADE = "PERM_TRADE";
    public static final String PERM_SELL = "PERM_SELL";

    private Authority() {}
}