package com.jjinmak.product.product.out;

import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import com.jjinmak.contracts.product.event.ProductWishChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_CHANGED;
import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_REGISTERED;
import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_WISH_CHANGED;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class ProductKafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void publish(ProductRegisteredEvent event) {
        kafkaTemplate.send(PRODUCT_REGISTERED, String.valueOf(event.productId()), event);
    }

    // 키를 productId로 해서 같은 상품의 등록·수정·삭제가 순서대로 전달되게 한다.
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void publish(ProductChangedEvent event) {
        kafkaTemplate.send(PRODUCT_CHANGED, String.valueOf(event.productId()), event);
    }

    // 키를 productId로 해서 같은 상품의 찜·찜 취소가 순서대로 전달되게 한다.
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void publish(ProductWishChangedEvent event) {
        kafkaTemplate.send(PRODUCT_WISH_CHANGED, String.valueOf(event.productId()), event);
    }
}
