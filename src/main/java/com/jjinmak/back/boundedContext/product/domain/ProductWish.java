package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 회원이 찜한 상품. 같은 상품은 한 번만 찜할 수 있고, 찜 해제 시 행을 삭제한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_product_wish_member_product", columnNames = {"member_id", "product_id"}))
public class ProductWish extends BaseIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private ProductMember member;

    private ProductWish(Product product, ProductMember member) {
        this.product = product;
        this.member = member;
    }

    // 본인이 등록한 상품은 찜할 수 없다.
    public static ProductWish create(Product product, ProductMember member) {
        if (product.getSeller().getId().equals(member.getId())) {
            throw new BusinessException(ProductErrorCode.CANNOT_WISH_OWN_PRODUCT);
        }
        return new ProductWish(product, member);
    }
}
