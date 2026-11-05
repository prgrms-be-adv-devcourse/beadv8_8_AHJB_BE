package com.jjinmak.member.in;

import com.jjinmak.member.app.MemberWarningReceiveUseCase;
import com.jjinmak.contracts.member.event.MemberWarningEvent;
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
