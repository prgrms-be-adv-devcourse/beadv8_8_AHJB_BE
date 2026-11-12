package com.jjinmak.chat.domain;

import com.jjinmak.common.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 도메인의 복제본. product.registered 이벤트로 받은 상품명·썸네일을 채팅방 생성 때 쓰기 위해 보관한다.
 * 경매 요청 이후 상품 정보는 수정되지 않으므로 한 번 저장하면 갱신하지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatProduct extends BaseManualIdAndTime {

    @Column(nullable = false, length = 100)
    private String name;

    private String thumbnailUrl;

    /**
     * @param productId 상품 도메인의 원본 id (이 엔티티의 PK로 그대로 쓴다)
     */
    public ChatProduct(Long productId, String name, String thumbnailUrl) {
        super(productId);
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
    }
}