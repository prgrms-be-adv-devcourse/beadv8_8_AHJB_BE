package com.jjinmak.boundedcontext.market.shared.in;

import com.jjinmak.boundedcontext.market.shared.app.MarketMemberSyncUseCase;
import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketMemberEventListener {

    private final MarketMemberSyncUseCase marketMemberSyncUseCase;

    // 회원 가입 시 회원 서비스가 보내는 이벤트로 market 회원을 복제한다.
    // (판매자·입찰자·찜·문의·후기 작성자 정보. 예전에는 상품·경매·후기가 각자 받았다)
    // 회원 id나 닉네임이 없으면 재시도해도 소용없어 바로 DLT로 보낸다.
    @KafkaListener(topics = MemberEventTopics.MEMBER_CREATED, groupId = "market")
    public void handle(MemberCreatedEvent event) {
        MemberDto member = event.member();
        if (member == null || member.id() == null || member.nickname() == null || member.nickname().isBlank()) {
            throw new InvalidEventException("회원 생성 이벤트에 회원 id나 닉네임이 없습니다. member=" + member);
        }
        marketMemberSyncUseCase.syncMember(member);
    }
}
