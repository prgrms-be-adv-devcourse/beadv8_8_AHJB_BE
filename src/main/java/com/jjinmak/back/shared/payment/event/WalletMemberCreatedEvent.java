package com.jjinmak.back.shared.payment.event;

import com.jjinmak.back.shared.payment.dto.WalletMemberDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WalletMemberCreatedEvent {
    private final WalletMemberDto member;
}
