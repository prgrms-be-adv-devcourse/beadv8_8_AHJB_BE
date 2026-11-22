package com.jjinmak.boundedcontext.market.product.out;

import com.jjinmak.boundedcontext.market.shared.out.MarketOutboxRecorder;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import com.jjinmak.contracts.product.event.ProductWishChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_CHANGED;
import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_REGISTERED;
import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_WISH_CHANGED;

// 상품 등록·변경·찜 이벤트를 market 아웃박스에 저장한다 (#548). 예전 ProductKafkaEventPublisher(AFTER_COMMIT 직접 전송)를 대신한다.
// 상품 등록 이벤트는 경매 열기 스케줄러 안에서 발행되어, 직접 전송일 때 브로커 중단 중 경매 열기가 44.7초 멈췄다(3차 측정).
// 키는 기존과 같이 productId (같은 상품의 등록·수정·삭제·찜이 순서대로 전달되게).
@Component
@RequiredArgsConstructor
public class ProductOutboxWriter {

    private final MarketOutboxRecorder recorder;

    @EventListener
    public void write(ProductRegisteredEvent event){
        recorder.record(PRODUCT_REGISTERED, event.productId(), event);
    }

    @EventListener
    public void write(ProductChangedEvent event){
        recorder.record(PRODUCT_CHANGED, event.productId(), event);
    }

    @EventListener
    public void write(ProductWishChangedEvent event){
        recorder.record(PRODUCT_WISH_CHANGED, event.productId(), event);
    }
}
