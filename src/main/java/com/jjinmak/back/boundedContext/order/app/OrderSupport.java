package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_NOT_FOUND;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;

@Component
@RequiredArgsConstructor
public class OrderSupport {

    private final OrderRepository orderRepository;
    private final OrderMemberRepository orderMemberRepository;

    public OrderMember getMember(Long memberId){
        return orderMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    public OrderMember getMember(UUID memberId){
        return orderMemberRepository.findByUuid(memberId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    public Order getOrderWithParticipants(Long orderId){
        return orderRepository.findByIdWithWinnerAndSeller(orderId)
                .orElseThrow(() -> new BusinessException(ORDER_NOT_FOUND));
    }
}
