package com.jjinmak.product.product.app;

import com.jjinmak.contracts.product.event.ProductWishChangeType;
import com.jjinmak.contracts.product.event.ProductWishChangedEvent;
import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductErrorCode;
import com.jjinmak.product.product.domain.ProductMember;
import com.jjinmak.product.product.domain.ProductWish;
import com.jjinmak.product.product.in.dto.ProductWishResponseDto;
import com.jjinmak.product.product.out.ProductMemberRepository;
import com.jjinmak.product.product.out.ProductRepository;
import com.jjinmak.product.product.out.ProductWishRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductWishCreateUseCase {

    private final ProductRepository productRepository;
    private final ProductMemberRepository productMemberRepository;
    private final ProductWishRepository productWishRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductWishResponseDto wish(Long memberId, Long productId){
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        ProductMember member = productMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        if (productWishRepository.existsByMemberIdAndProductId(memberId, productId)){
            throw new BusinessException(ProductErrorCode.ALREADY_WISHED);
        }

        try {
            productWishRepository.saveAndFlush(ProductWish.create(product, member));
        } catch (DataIntegrityViolationException e) {
            // 같은 요청이 동시에 들어와 위 검사를 함께 통과한 경우, 유니크 제약이 막는다.
            throw new BusinessException(ProductErrorCode.ALREADY_WISHED);
        }

        productRepository.increaseWishCount(productId);

        // 찜 등록 이벤트 (실제 Kafka 전송은 커밋 후)
        eventPublisher.publishEvent(new ProductWishChangedEvent(
                productId, memberId, ProductWishChangeType.WISHED, product.getCurrentAuctionId(), LocalDateTime.now()));

        return new ProductWishResponseDto(productId, true, productRepository.findWishCount(productId));
    }
}
