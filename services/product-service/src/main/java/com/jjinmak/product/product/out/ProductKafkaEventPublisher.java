package com.jjinmak.product.product.out;

import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_REGISTERED;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class ProductKafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void publish(ProductRegisteredEvent event) {
        kafkaTemplate.send(PRODUCT_REGISTERED, String.valueOf(event.productId()), event);
    }
}
