package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.out.OrderGroupRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.*;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.*;

@Service
@RequiredArgsConstructor
public class OrderPaymentResultUseCase {

    private final OrderGroupRepository orderGroupRepository;

    public void paymentSucceed(Long groupId){

        OrderGroup group = orderGroupRepository.findByIdWithOrders(groupId)
                .orElseThrow(() -> new BusinessException(ORDER_GROUP_NOT_FOUND));

        List<Order> orders = group.getOrders();

        for (Order order : orders){
            // TODO: 만약 결제는 했는데, 저장된 상태가 WAITING이 아니면 어떻게 하지?

            order.updateState(PAID);
        }

        LocalDateTime paidAt = LocalDateTime.now();

        // TODO: 결제성공 이벤트 발행
        for (Order order : orders){
            OrderPaymentSucceedEvent event = new OrderPaymentSucceedEvent(order.getId(), order.getProductId(), paidAt);
        }
    }
}
