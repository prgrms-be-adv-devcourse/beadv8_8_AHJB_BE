package com.jjinmak.back.boundedContext.wallet.app;


import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import com.jjinmak.back.boundedContext.wallet.out.WalletMemberRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.wallet.event.WalletMemberCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EventListener;

@Service
@RequiredArgsConstructor
public class WalletSyncMemberUseCase {
    private final WalletMemberRepository walletMemberRepository;
    private final EventPublisher eventPublisher;

    public WalletMember syncMember(MemberDto member) {
        boolean isNew = !walletMemberRepository.existsById(member.id());

        WalletMember _member = walletMemberRepository.save(
                new WalletMember(
                        member.id(),
                        member.uuid()
                )
        );

        if (isNew) {
            eventPublisher.publish(
                    new WalletMemberCreatedEvent(
                            _member.toDto()
                    )
            );
        }

        return _member;
    }
}
