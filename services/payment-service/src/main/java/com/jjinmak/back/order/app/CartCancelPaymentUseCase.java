package com.jjinmak.back.order.app;

import com.jjinmak.back.order.domain.OrderGroup;
import com.jjinmak.back.order.out.payment.PaymentRequest;
import com.jjinmak.back.order.out.repository.OrderGroupRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentCloseResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.back.order.exception.OrderErrorCode.ORDER_GROUP_FORBIDDEN;
import static com.jjinmak.back.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class CartCancelPaymentUseCase {

    private final PaymentRequest paymentRequest;
    private final OrderGroupRepository orderGroupRepository;
    private final OrderSupport orderSupport;

    public void cancelPayment(Long winnerId, Long orderGroupId){

        OrderGroup orderGroup = orderSupport.getOrderGroup(orderGroupId);

        if (!orderGroup.getWinner().getId().equals(winnerId))
            throw new BusinessException(ORDER_GROUP_FORBIDDEN);

        PaymentCloseResultDto result = paymentRequest.closePayment(orderGroupId, winnerId);

        if (!result.canDiscardOrderGroup())
            throw new BusinessException(ORDER_STATE_BAD_REQUEST);

        orderGroupRepository.delete(orderGroup);
    }
}
