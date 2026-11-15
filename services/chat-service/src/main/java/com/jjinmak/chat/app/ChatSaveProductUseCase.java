package com.jjinmak.chat.app;

import com.jjinmak.chat.domain.ChatProduct;
import com.jjinmak.chat.out.ChatProductRepository;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatSaveProductUseCase {

    private final ChatProductRepository chatProductRepository;

    /**
     * 경매 요청 이벤트를 받아 상품명·썸네일 복제본을 저장한다.
     * 같은 상품의 복제본이 이미 있으면 아무것도 하지 않는다 (Kafka 중복 수신 대비).
     *
     * @param event - 상품 ID, 상품명, 썸네일 URL (그 외 필드는 채팅에서 쓰지 않는다)
     */
    public void saveProduct(ProductRegisteredEvent event) {
        if (chatProductRepository.existsById(event.productId())) {
            return;
        }

        chatProductRepository.save(new ChatProduct(event.productId(), event.productName(), event.thumbnailUrl()));
    }
}