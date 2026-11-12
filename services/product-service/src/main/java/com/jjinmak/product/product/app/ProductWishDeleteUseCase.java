package com.jjinmak.product.product.app;

import com.jjinmak.contracts.product.event.ProductWishChangeType;
import com.jjinmak.contracts.product.event.ProductWishChangedEvent;
import com.jjinmak.product.product.domain.ProductErrorCode;
import com.jjinmak.product.product.domain.ProductWish;
import com.jjinmak.product.product.in.dto.ProductWishResponseDto;
import com.jjinmak.product.product.out.ProductRepository;
import com.jjinmak.product.product.out.ProductWishRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductWishDeleteUseCase {

    private final ProductRepository productRepository;
    private final ProductWishRepository productWishRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductWishResponseDto unwish(Long memberId, Long productId){
        ProductWish wish = productWishRepository.findByMemberIdAndProductId(memberId, productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.WISH_NOT_FOUND));

        Long auctionId = wish.getProduct().getCurrentAuctionId();

        productWishRepository.delete(wish);

        productRepository.decreaseWishCount(productId);

        // 찜 취소 이벤트 (실제 Kafka 전송은 커밋 후)
        eventPublisher.publishEvent(new ProductWishChangedEvent(
                productId, memberId, ProductWishChangeType.UNWISHED, auctionId, LocalDateTime.now()));

        return new ProductWishResponseDto(productId, false, productRepository.findWishCount(productId));
    }
}
