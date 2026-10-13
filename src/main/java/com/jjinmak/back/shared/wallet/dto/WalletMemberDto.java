package com.jjinmak.back.shared.wallet.dto;

import java.util.UUID;


public record WalletMemberDto(
        Long id,
        UUID uuid
) {
}
