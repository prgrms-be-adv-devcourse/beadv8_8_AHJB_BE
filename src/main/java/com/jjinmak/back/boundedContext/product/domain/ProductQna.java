package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 상품 Q&A 댓글. parent가 null이면 질문, 값이 있으면 질문에 달린 답글 (1단계까지만)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductQna extends BaseIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private ProductMember writer;

    @ManyToOne(fetch = FetchType.LAZY)
    private ProductQna parent;

    @Column(name = "product_qna_content", nullable = false, columnDefinition = "TEXT")
    private String content;

    private LocalDateTime deletedAt;

    private ProductQna(Product product, ProductMember writer, ProductQna parent, String content) {
        this.product = product;
        this.writer = writer;
        this.parent = parent;
        this.content = content;
    }

    public static ProductQna createQuestion(Product product, ProductMember writer, String content) {
        return new ProductQna(product, writer, null, content);
    }

    // 답글은 상품 판매자만, 같은 상품의 삭제되지 않은 질문에만 달 수 있다.
    public static ProductQna createReply(Product product, ProductMember writer, ProductQna parent, String content) {
        if (!product.getSeller().getId().equals(writer.getId())) {
            throw new BusinessException(ProductErrorCode.ONLY_SELLER_CAN_REPLY);
        }
        if (!parent.isQuestion()
                || parent.isDeleted()
                || !parent.getProduct().getId().equals(product.getId())) {
            throw new BusinessException(ProductErrorCode.INVALID_QNA_PARENT);
        }
        return new ProductQna(product, writer, parent, content);
    }

    public boolean isQuestion() {
        return parent == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isWrittenBy(Long memberId) {
        return writer.getId().equals(memberId);
    }

    public void update(Long writerId, String content) {
        validateWriter(writerId);
        this.content = content;
    }

    public void delete(Long writerId, LocalDateTime now) {
        validateWriter(writerId);
        this.deletedAt = now;
    }

    private void validateWriter(Long writerId) {
        if (!isWrittenBy(writerId)) {
            throw new BusinessException(ProductErrorCode.NOT_QNA_WRITER);
        }
    }
}
