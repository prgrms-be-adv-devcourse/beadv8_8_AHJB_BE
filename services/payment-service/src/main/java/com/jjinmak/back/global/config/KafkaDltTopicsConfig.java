package com.jjinmak.back.global.config;

import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.delivery.event.DeliveryEventTopics;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.order.event.OrderEventTopics;
import com.jjinmak.contracts.payment.event.PaymentEventTopics;
import com.jjinmak.contracts.product.event.ProductEventTopics;
import com.jjinmak.contracts.refund.event.RefundEventTopics;
import com.jjinmak.contracts.settlement.event.SettlementEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaDltTopicsConfig {

    private static final String DLT_SUFFIX = "-dlt";

    @Bean
    KafkaAdmin.NewTopics dltTopics() {
        return new KafkaAdmin.NewTopics(
                // member (주문, 결제)
                TopicBuilder.name(dlt(MemberEventTopics.MEMBER_CREATED)).build(),

                // auction (주문)
                TopicBuilder.name(dlt(AuctionEventTopics.AUCTION_WON)).build(),

                // product (정산)
                TopicBuilder.name(dlt(ProductEventTopics.PRODUCT_REGISTERED)).build(),

                // delivery (주문)
                TopicBuilder.name(dlt(DeliveryEventTopics.DELIVERY_CREATED)).build(),
                TopicBuilder.name(dlt(DeliveryEventTopics.DELIVERY_STATUS_UPDATED)).build(),

                // order (결제, 정산, 환불)
                TopicBuilder.name(dlt(OrderEventTopics.ORDER_CONFIRM_PURCHASE)).build(),
                TopicBuilder.name(dlt(OrderEventTopics.ORDER_GROUP_DELETED)).build(),
                TopicBuilder.name(dlt(OrderEventTopics.ORDER_REFUND_REQUESTED)).build(),

                // payment (주문, 정산, 환불)
                TopicBuilder.name(dlt(PaymentEventTopics.PAYMENT_PAID_EVENT)).build(),
                TopicBuilder.name(dlt(PaymentEventTopics.PAYMENT_RELEASED_EVENT)).build(),
                TopicBuilder.name(dlt(PaymentEventTopics.PAYMENT_REFUND_EVENT)).build(),

                // refund (주문, 결제)
                TopicBuilder.name(dlt(RefundEventTopics.REFUND_ACCEPTED)).build(),
                TopicBuilder.name(dlt(RefundEventTopics.REFUND_REJECTED)).build(),
                TopicBuilder.name(dlt(RefundEventTopics.REFUND_RETRY_REQUESTED)).build(),

                // settlement (결제)
                TopicBuilder.name(dlt(SettlementEventTopics.SETTLEMENT_REQUESTED)).build()
        );
    }

    private static String dlt(String topic) {
        return topic + DLT_SUFFIX;
    }
}
