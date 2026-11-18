package com.jjinmak.contracts.payment.event;

import com.jjinmak.contracts.payment.dto.WalletMemberDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WalletMemberCreatedEvent {
    private final WalletMemberDto member;
}
