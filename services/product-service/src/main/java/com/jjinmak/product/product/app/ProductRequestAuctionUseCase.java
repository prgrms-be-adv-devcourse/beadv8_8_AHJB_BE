package com.jjinmak.product.product.app;

import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductAuctionStatus;
import com.jjinmak.product.product.domain.ProductAuctionTerms;
import com.jjinmak.product.product.domain.ProductErrorCode;
import com.jjinmak.product.product.out.ProductRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRequestAuctionUseCase {

    private static final String ENGLISH = "ENGLISH";

    // 요청 후 이 시간이 지나도 경매가 시작되지 않으면 요청 이벤트가 유실된 것으로 보고 다시 보낸다.
    // (경매는 요청을 받자마자 만들어지고 시작 이벤트를 보내므로 보통 몇 초 안에 끝난다)
    public static final Duration RESEND_AFTER = Duration.ofMinutes(5);
    // 요청 후 이 시간까지만 다시 보낸다. 경매가 일부러 거절한 상품(DLT)을 계속 보내지 않기 위해서다.
    public static final Duration RESEND_UNTIL = Duration.ofHours(1);

    private final ProductRepository productRepository;
    private final ProductReadThumbnailUseCase productReadThumbnailUseCase;
    private final ApplicationEventPublisher eventPublisher;

    public List<Long> findTargetIds(LocalDateTime now){
        return productRepository.findAuctionRequestTargetIds(now);
    }

    public List<Long> findResendTargetIds(LocalDateTime now){
        return productRepository.findAuctionResendTargetIds(
                now.minus(RESEND_UNTIL), now.minus(RESEND_AFTER), ProductAuctionStatus.SCHEDULED);
    }

    // 시작 예정 시각이 된 상품의 경매 생성을 경매 컨텍스트에 요청한다.
    // 요청 표시는 저장되고 이벤트는 커밋 후 전송된다. 전송이 실패하면 resendAuction이 다시 보낸다.
    public void requestAuction(Long productId, LocalDateTime now){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        // 조회 이후 삭제되었거나 이미 요청된 경우
        if (!product.isAuctionRequestable(now)){
            return;
        }

        product.requestAuction(now);

        publishAuctionRequest(product);
    }

    // 요청했는데 경매가 시작되지 않은 상품의 요청을 다시 보낸다.
    // 경매는 이미 만든 상품의 요청을 무시하므로, 실제로는 만들어졌는데 시작 이벤트만 늦은 경우에도 안전하다.
    public void resendAuction(Long productId){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        // 조회 이후 경매 시작 이벤트가 도착한 경우
        if (!product.isAuctionResendable()){
            return;
        }

        log.warn("경매 요청 재전송: productId={}, requestedAt={}", productId, product.getAuctionRequestedAt());
        publishAuctionRequest(product);
    }

    // 시작·종료 시각을 상품에서 확정해서 보낸다. (이벤트가 늦게 도착해도 안내한 시각 그대로 경매가 열린다)
    // 옛 필드(instantWinPrice, duration, customEndAt)는 비워 보낸다. 경매가 더 이상 읽지 않는다.
    // 상품명·썸네일은 정산·채팅이 아직 이 이벤트에서 읽으므로 계속 채운다.
    private void publishAuctionRequest(Product product){
        ProductAuctionTerms auctionTerms = product.getAuctionTerms();
        LocalDateTime scheduledAt = product.getAuctionScheduledAt();

        eventPublisher.publishEvent(new ProductRegisteredEvent(
                product.getId(),
                product.getSeller().getId(),
                product.getShippingFee(),
                auctionTerms.getStartPrice(),
                null,
                0,
                null,
                product.getName(),
                product.getCategory().name(),
                productReadThumbnailUseCase.findThumbnailUrl(product.getId()),
                ENGLISH,
                scheduledAt,
                new ProductRegisteredEvent.EnglishTerms(
                        auctionTerms.getInstantWinPrice(),
                        auctionTerms.resolveEndAt(scheduledAt)),
                null
        ));
    }
}
