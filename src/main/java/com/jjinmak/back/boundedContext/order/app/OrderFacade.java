package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.app.dto.OrderDto;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.shared.cart.event.CartCreateOrderEvent;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.refund.dto.RefundDto;
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
    private final OrderPaymentResultUseCase orderPaymentResultUseCase;
    private final OrderRequestRefundUseCase orderRequestRefundUseCase;
    private final OrderRefundResultUseCase orderRefundResultUseCase;
    private final OrderDeliveryUseCase orderDeliveryUseCase;
    private final OrderSchedulerUseCase orderSchedulerUseCase;

    @Transactional
    public OrderMember syncMember(MemberDto memberDto){
        return orderSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public void createOrder(CartCreateOrderEvent event){
        orderPaymentUseCase.tryPayment(event);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> readWinnerOrders(Long winnerId){
        return orderReadOrderUseCase.readWinnerOrders(winnerId);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> readSellerOrders(Long sellerId){
        return orderReadOrderUseCase.readSellerOrders(sellerId);
    }

    @Transactional(readOnly = true)
    public OrderDto readOrder(Long memberId, Long orderId){
        return orderReadOrderUseCase.readOrder(memberId, orderId);
    }

    @Transactional
    public void confirmPurchase(Long memberId, Long orderId){
        orderConfirmPurchaseUseCase.confirmPurchase(memberId, orderId);
    }

    @Transactional
    public void paymentSucceed(Long groupId){
        orderPaymentResultUseCase.paymentSucceed(groupId);
    }

    @Transactional
    public void paymentFailed(Long groupId){
        orderPaymentResultUseCase.paymentFailed(groupId);
    }

    @Transactional
    public void requestRefund(Long memberId, Long orderId){
        orderRequestRefundUseCase.requestRefund(memberId, orderId);
    }

    @Transactional
    public void acceptRefund(RefundDto refund){
        orderRefundResultUseCase.acceptRefund(refund);
    }

    @Transactional
    public void rejectRefund(RefundDto refund){
        orderRefundResultUseCase.rejectRefund(refund);
    }

    @Transactional(readOnly = true)
    public void requestDelivery(Long sellerId, Long orderId, CarrierType carrier, String waybill){
        orderDeliveryUseCase.requestDelivery(sellerId, orderId, carrier, waybill);
    }

    @Transactional
    public void startDelivery(DeliveryCreatedEvent event){
        orderDeliveryUseCase.startDelivery(event);
    }

    @Transactional(readOnly = true)
    public List<Long> getExpiredOrderIds(){
        return orderSchedulerUseCase.getExpiredOrderIds();
    }

    @Transactional
    public void confirmPurchaseAuto(Long orderId){
        orderSchedulerUseCase.confirmPurchaseAuto(orderId);
    }

    @Transactional
    public void deleteOverWaitingOrders(){
        orderSchedulerUseCase.deleteOverWaitingOrders();
    }

    @Transactional
    public void updateDeliveryStatus(DeliveryStatusUpdatedEvent event){
        orderDeliveryUseCase.updateDeliveryStatus(event);
    }
}
