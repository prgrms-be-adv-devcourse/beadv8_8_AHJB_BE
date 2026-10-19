package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.app.dto.PaymentResponseDto;
import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.payment.PaymentRequest;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import com.jjinmak.back.shared.payment.event.PaymentPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.*;

@Service
@RequiredArgsConstructor
public class CartRequestPaymentUseCase {

    private final EventPublisher eventPublisher;
    private final PaymentRequest paymentRequest;
    private final OrderSupport orderSupport;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderGroupRepository orderGroupRepository;

    /**
     * 낙찰자가 해당하는 상품을 보유하고 있는지 검사, 주문서를 생성한 후 결제 컨텍스트에 요청
     * @param winnerId   - 조회의 주체가 되는 낙찰자
     * @param productIds - 결제를 시도하는 상품의 id 리스트
     */
    public PaymentRequestDto createOrder(Long winnerId, List<Long> productIds){
        OrderMember winner = orderSupport.getMember(winnerId);

        Set<Long> productIdSet = new HashSet<>(productIds);

        List<CartItem> cartItems = cartItemRepository.findAllByWinnerAndProductIdIn(winner, productIdSet);

        if (productIdSet.size() != cartItems.size()){
            throw new BusinessException(CART_ITEM_FORBIDDEN);
        }

        LocalDateTime now = LocalDateTime.now();

        if (cartItems.stream().anyMatch(i -> i.getPaymentDueAt().isBefore(now))){
            throw new BusinessException(CART_ITEM_PAYMENT_EXPIRED);
        }

        long totalPrice = cartItems.stream()
                .mapToLong(i -> i.getWinningPrice() + i.getDeliveryFee())
                .sum();

        OrderGroup orderGroup = orderGroupRepository.save(new OrderGroup(totalPrice));

        List<PaymentRequestDto.Item> items = new ArrayList<>();

        for (CartItem item : cartItems){
            OrderMember seller = item.getSeller();

            Order order = new Order(
                    orderGroup, winner, seller,
                    item.getAuctionId(), item.getProductId(),
                    item.getWinningPrice(), item.getDeliveryFee()
            );

            orderGroup.add(orderRepository.save(order));

            items.add(new PaymentRequestDto.Item(order.getId(), order.getSeller().getId(), order.getWinningPrice(), order.getDeliveryFee()));
        }

        return new PaymentRequestDto(orderGroup.getId(), winnerId, totalPrice, items);
    }

    public PaymentResultDto requestPayment(PaymentRequestDto request){
        return paymentRequest.requestPayment(request);
    }

    public PaymentResponseDto handlePaymentResult(Long orderGroupId, PaymentResultDto dto){
        if (dto.status() == PaymentResultDto.Status.PAID){
            return new PaymentResponseDto(orderGroupId, true, null);
        }
        else {
            PaymentResponseDto.Data data = new PaymentResponseDto.Data(
                    dto.paymentId(), dto.totalPrice(), dto.chargeAmount(),
                    dto.pgOrderId(), dto.orderName()
            );
            return new PaymentResponseDto(orderGroupId, false, data);
        }
    }

    public void paymentPaid(PaymentPaidEvent event){
        Long orderGroupId = event.orderGroupId();
        Long winnerId = event.buyerId();
        LocalDateTime paidAt = event.paidAt();

        OrderGroup orderGroup = orderGroupRepository.findByIdWithOrders(orderGroupId)
                .orElseThrow(() -> new BusinessException(ORDER_GROUP_NOT_FOUND));

        List<Order> orders = orderGroup.getOrders();

        if (!orders.getFirst().getWinner().getId().equals(winnerId))
            throw new BusinessException(ORDER_GROUP_FORBIDDEN);

        for (Order order : orders){
            order.paid(paidAt);

            CartItem item = cartItemRepository.findByAuctionId(order.getAuctionId())
                    .orElseThrow(() -> new BusinessException(CART_ITEM_NOT_FOUND));

            cartItemRepository.delete(item);

            eventPublisher.publish(new OrderPaymentSucceedEvent(
                    order.getId(), order.getAuctionId(), order.getProductId(), paidAt
            ));
        }
    }
}
