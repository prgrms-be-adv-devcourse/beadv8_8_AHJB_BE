package com.jjinmak.payment.app.usecase;


import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.payment.event.WalletMemberCreatedEvent;
import com.jjinmak.payment.domain.WalletMember;
import com.jjinmak.payment.out.eventPublisher.EventPublisher;
import com.jjinmak.payment.out.repository.WalletMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletSyncMemberUseCase {
    private final WalletMemberRepository walletMemberRepository;
    private final EventPublisher eventPublisher;

    /**
     * 회원 생성 이벤트로 지갑 회원을 복제한다. 새 회원이면 WalletMemberCreatedEvent 를 내서 지갑을 만들게 한다.
     *
     * 이미 복제된 회원이면 아무 것도 하지 않고 그대로 돌려준다 (멱등).
     * Kafka 는 같은 이벤트를 두 번 줄 수 있는데, 같은 id 로 다시 저장하면 새 행으로 INSERT 를 시도해 PK 중복으로 실패한다.
     */
    public WalletMember syncMember(MemberDto member) {
        Optional<WalletMember> existing = walletMemberRepository.findById(member.id());
        if (existing.isPresent()) {
            return existing.get();
        }

        WalletMember _member = walletMemberRepository.save(
                new WalletMember(
                        member.id(),
                        member.uuid()
                )
        );

        eventPublisher.publish(
                new WalletMemberCreatedEvent(
                        _member.toDto()
                )
        );

        return _member;
    }
}
