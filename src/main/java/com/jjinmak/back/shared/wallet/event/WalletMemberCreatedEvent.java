package com.jjinmak.back.shared.wallet.event;

import com.jjinmak.back.shared.wallet.dto.WalletMemberDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WalletMemberCreatedEvent {
    private final WalletMemberDto member;
}
