package com.jjinmak.payment.app;


import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.payment.event.WalletMemberCreatedEvent;
import com.jjinmak.payment.domain.WalletMember;
import com.jjinmak.payment.out.eventPublisher.EventPublisher;
import com.jjinmak.payment.out.WalletMemberRepository;
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
