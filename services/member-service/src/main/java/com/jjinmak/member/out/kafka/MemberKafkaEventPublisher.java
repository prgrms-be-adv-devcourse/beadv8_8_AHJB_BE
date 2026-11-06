package com.jjinmak.member.out.kafka;

import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.member.event.MemberSanctionEvent;
import com.jjinmak.member.domain.MemberSanction;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.contracts.member.event.MemberEventTopics.MEMBER_SANCTIONED;
import static com.jjinmak.contracts.member.event.MemberEventTopics.MEMBER_CREATED;

@Component
@RequiredArgsConstructor
public class MemberKafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(MemberCreatedEvent event){
        kafkaTemplate.send(MEMBER_CREATED, String.valueOf(event.member().id()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(MemberSanctionEvent event){
        kafkaTemplate.send(MEMBER_SANCTIONED, String.valueOf(event.memberSanction().memberId()), event);
    }

}
