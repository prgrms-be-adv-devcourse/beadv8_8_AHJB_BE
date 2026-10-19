package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.app.dto.OrderDto;
import com.jjinmak.back.boundedContext.order.app.dto.PaymentResponseDto;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderSyncMemberUseCase orderSyncMemberUseCase;
    private final OrderReadOrderUseCase orderReadOrderUseCase;
    private final OrderConfirmPurchaseUseCase orderConfirmPurchaseUseCase;
    private final OrderRefundUseCase orderRefundUseCase;
    private final OrderDeliveryUseCase orderDeliveryUseCase;
    private final OrderSchedulerUseCase orderSchedulerUseCase;
    private final CartItemUseCase cartItemUseCase;
    private final CartRequestPaymentUseCase cartRequestPaymentUseCase;
    private final CartDefaultPaymentUseCase cartDefaultPaymentUseCase;

    @Transactional
    public OrderMember syncMember(MemberDto memberDto){
        return orderSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public void createCartItem(AuctionWonEvent event){
        cartItemUseCase.createCartItem(event);
    }

    @Transactional(readOnly = true)
    public List<CartItemDto> readWinnerItem(Long winnerId){
        return cartItemUseCase.readWinnerItems(winnerId);
    }

    @Transactional
    public PaymentRequestDto createOrder(Long winnerId, List<Long> productIds){
        return cartRequestPaymentUseCase.createOrder(winnerId, productIds);
    }

    public PaymentResultDto requestPayment(PaymentRequestDto request){
        return cartRequestPaymentUseCase.requestPayment(request);
    }

    @Transactional
    public PaymentResponseDto handlePaymentResult(Long orderGroupId, PaymentResultDto dto){
        return cartRequestPaymentUseCase.handlePaymentResult(orderGroupId, dto);
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredItemIds(){
        return cartDefaultPaymentUseCase.findExpiredItemIds();
    }

    @Transactional
    public void deleteExpiredItem(Long itemId){
        cartDefaultPaymentUseCase.deleteExpiredItem(itemId);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> readWinnerOrders(Long winnerId, int page, int size){
        return orderReadOrderUseCase.readWinnerOrders(winnerId, page, size);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> readSellerOrders(Long sellerId, int page, int size){
        return orderReadOrderUseCase.readSellerOrders(sellerId, page, size);
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
    public void requestRefund(Long memberId, Long orderId){
        orderRefundUseCase.requestRefund(memberId, orderId);
    }

    @Transactional
    public void acceptRefund(RefundDto refund){
        orderRefundUseCase.acceptRefund(refund);
    }

    @Transactional
    public void rejectRefund(RefundDto refund){
        orderRefundUseCase.rejectRefund(refund);
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
