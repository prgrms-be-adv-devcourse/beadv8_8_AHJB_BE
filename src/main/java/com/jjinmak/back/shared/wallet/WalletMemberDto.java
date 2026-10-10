package com.jjinmak.back.shared.wallet;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class WalletMemberDto {
    private final Long id;
    private final UUID uuid;
}
