package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.app.dto.OrderDto;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.shared.cart.dto.CartCreateOrderDto;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderSyncMemberUseCase orderSyncMemberUseCase;
    private final OrderPaymentUseCase orderPaymentUseCase;
    private final OrderReadOrderUseCase orderReadOrderUseCase;
    private final OrderConfirmPurchaseUseCase orderConfirmPurchaseUseCase;

    @Transactional
    public OrderMember syncMember(MemberDto memberDto){
        return orderSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public OrderGroup createOrder(CartCreateOrderDto dto){
        return orderPaymentUseCase.tryPayment(dto);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> readWinnerOrders(UUID winnerId){
        return orderReadOrderUseCase.readWinnerOrders(winnerId);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> readSellerOrders(UUID sellerId){
        return orderReadOrderUseCase.readSellerOrders(sellerId);
    }

    @Transactional(readOnly = true)
    public OrderDto readOrder(UUID memberId, Long orderId){
        return orderReadOrderUseCase.readOrder(memberId, orderId);
    }

    @Transactional
    public void confirmPurchase(UUID memberId, Long orderId){
        orderConfirmPurchaseUseCase.confirmPurchase(memberId, orderId);
    }
}
