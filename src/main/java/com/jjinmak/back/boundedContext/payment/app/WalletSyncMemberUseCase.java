package com.jjinmak.back.boundedContext.payment.app;


import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.boundedContext.payment.out.WalletMemberRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.payment.event.WalletMemberCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
