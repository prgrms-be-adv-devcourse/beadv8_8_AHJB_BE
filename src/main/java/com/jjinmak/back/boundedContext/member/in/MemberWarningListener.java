package com.jjinmak.back.boundedContext.member.in;

import com.jjinmak.back.boundedContext.member.app.MemberWarningReceiveUseCase;
import com.jjinmak.back.shared.member.event.MemberWarningEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MemberWarningListener {

    private final MemberWarningReceiveUseCase receiveUseCase;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(MemberWarningEvent event){
        receiveUseCase.receiveWarning(event.warning());
    }
}
