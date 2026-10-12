package com.jjinmak.back.shared.delivery.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CarrierType {
    CJ("대한통운")
    ;

    private final String korean;
}
