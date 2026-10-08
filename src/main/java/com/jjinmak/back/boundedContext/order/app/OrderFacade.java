package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.shared.cart.dto.CartCreateOrderDto;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderSyncMemberUseCase orderSyncMemberUseCase;
    private final OrderPaymentUseCase orderPaymentUseCase;

    @Transactional
    public OrderMember syncMember(MemberDto memberDto){
        return orderSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public OrderGroup createOrder(CartCreateOrderDto dto){
        return orderPaymentUseCase.tryPayment(dto);
    }
}
