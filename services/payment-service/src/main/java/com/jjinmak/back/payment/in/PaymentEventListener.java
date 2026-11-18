package com.jjinmak.back.payment.in;


import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderEventTopics;
import com.jjinmak.contracts.order.event.OrderGroupDeletedEvent;
import com.jjinmak.contracts.payment.event.WalletMemberCreatedEvent;
import com.jjinmak.contracts.refund.dto.RefundDto;
import com.jjinmak.contracts.refund.event.RefundAcceptedEvent;
import com.jjinmak.contracts.refund.event.RefundEventTopics;
import com.jjinmak.contracts.refund.event.RefundRetryRequestedEvent;
import com.jjinmak.contracts.settlement.dto.SettlementDto;
import com.jjinmak.contracts.settlement.event.SettlementEventTopics;
import com.jjinmak.contracts.settlement.event.SettlementRequestedEvent;
import com.jjinmak.back.payment.app.facade.WalletFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final String GROUP_ID = "payment";

    private final WalletFacade walletFacade;

    // ===== 다른 서비스의 이벤트 (Kafka) =====

    @KafkaListener(topics = MemberEventTopics.MEMBER_CREATED, groupId = GROUP_ID)
    public void handle(MemberCreatedEvent event) {
        MemberDto member = required(event.member(), "member", event);
        required(member.id(), "member.id", event);
        required(member.uuid(), "member.uuid", event);
        walletFacade.syncMember(member);
    }

    @KafkaListener(topics = RefundEventTopics.REFUND_ACCEPTED, groupId = GROUP_ID)
    public void handle(RefundAcceptedEvent event) {
        refund(event.refund(), event);
    }

    @KafkaListener(topics = RefundEventTopics.REFUND_RETRY_REQUESTED, groupId = GROUP_ID)
    public void handle(RefundRetryRequestedEvent event) {
        refund(event.refund(), event);
    }

    @KafkaListener(topics = OrderEventTopics.ORDER_CONFIRM_PURCHASE, groupId = GROUP_ID)
    public void handle(OrderConfirmPurchaseEvent event) {
        walletFacade.confirmHolding(required(event.orderId(), "orderId", event));
    }

    @KafkaListener(topics = SettlementEventTopics.SETTLEMENT_REQUESTED, groupId = GROUP_ID)
    public void handle(SettlementRequestedEvent event) {
        SettlementDto settlement = required(event.settlement(), "settlement", event);
        walletFacade.releaseHolding(
                required(settlement.orderId(), "settlement.orderId", event),
                settlement.settlementAmount(),
                settlement.feeAmount());
    }

    @KafkaListener(topics = OrderEventTopics.ORDER_GROUP_DELETED, groupId = GROUP_ID)
    public void handle(OrderGroupDeletedEvent event) {
        walletFacade.closePaymentByOrderGroupDeleted(required(event.orderGroupId(), "orderGroupId", event));
    }

    // ===== 결제 서비스 안의 이벤트 (스프링 이벤트) =====

    @EventListener
    public void handle(WalletMemberCreatedEvent event) {
        walletFacade.createWallet(event.getMember());
    }

    private void refund(RefundDto refund, Object event) {
        required(refund, "refund", event);
        walletFacade.refund(
                required(refund.refundId(), "refund.refundId", event),
                required(refund.orderId(), "refund.orderId", event),
                required(refund.amount(), "refund.amount", event));
    }

    private static <T> T required(T value, String field, Object event) {
        if (value == null) {
            throw new InvalidEventException("이벤트에 " + field + " 값이 없습니다. event=" + event);
        }
        return value;
    }
}
