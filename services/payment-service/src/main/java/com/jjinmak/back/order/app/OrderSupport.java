package com.jjinmak.back.order.app;

import com.jjinmak.back.order.domain.Order;
import com.jjinmak.back.order.domain.OrderGroup;
import com.jjinmak.back.order.domain.OrderMember;
import com.jjinmak.back.order.out.repository.OrderGroupRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.order.out.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.jjinmak.back.order.exception.OrderErrorCode.*;
import static com.jjinmak.common.exception.CommonErrorCode.USER_NOT_FOUND;

@Component
@RequiredArgsConstructor
public class OrderSupport {

    private final OrderRepository orderRepository;
    private final OrderMemberRepository orderMemberRepository;
    private final OrderGroupRepository orderGroupRepository;

    public OrderMember getMember(Long memberId){
        return orderMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    public OrderMember getMember(UUID memberId){
        return orderMemberRepository.findByUuid(memberId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    public Order getOrder(Long orderId){
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ORDER_NOT_FOUND));
    }

    public Order getOrderWithParticipants(Long orderId){
        return orderRepository.findByIdWithWinnerAndSeller(orderId)
                .orElseThrow(() -> new BusinessException(ORDER_NOT_FOUND));
    }

    public OrderGroup getOrderGroup(Long orderGroupId){
        return orderGroupRepository.findById(orderGroupId)
                .orElseThrow(() -> new BusinessException(ORDER_GROUP_NOT_FOUND));
    }
}
