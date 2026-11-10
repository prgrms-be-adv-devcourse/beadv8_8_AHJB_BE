package com.jjinmak.auction.in;

import com.jjinmak.auction.app.AuctionFacade;
import com.jjinmak.auction.domain.AuctionType;
import com.jjinmak.auction.dto.AuctionCreateDto;
import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.contracts.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import static com.jjinmak.contracts.order.event.OrderEventTopics.*;
import static com.jjinmak.contracts.product.event.ProductEventTopics.*;


@Component
@RequiredArgsConstructor
public class AuctionEventListener {
    private final AuctionFacade auctionFacade;

    @KafkaListener(topics = PRODUCT_REGISTERED, groupId = "auction")
    public void handleProductRegistered(ProductRegisteredEvent event) {
        AuctionCreateDto dto = toCreateDto(event, LocalDateTime.now());
        try {
            auctionFacade.createAuction(dto);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException(
                    "경매 생성 조건이 올바르지 않습니다. productId=" + event.productId() + ", " + e.getMessage());
        }
    }
    // 회원 생성 시 경매 회원을 복제한다. (입찰자 확인, 입찰 내역 닉네임)
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handleMemberCreated(MemberCreatedEvent event) {
        auctionFacade.syncMember(event.member());
    }

    //낙찰 이후 거래 결과
    @KafkaListener(topics = ORDER_PAYMENT_SUCCEED, groupId = "auction")
    public void handleOrderPaymentSucceed(OrderPaymentSucceedEvent event) {
        auctionFacade.applyPaymentSucceeded(event.auctionId());
    }

    @KafkaListener(topics = CART_PAYMENT_DEFAULT, groupId ="auction")
    public void handleCartPaymentDefault(CartPaymentDefaultEvent event) {
        auctionFacade.applyPaymentDefaulted(event.auctionId());
    }

    @KafkaListener(topics = ORDER_CONFIRM_PURCHASE, groupId = "auction")
    public void handleOrderConfirmPurchase(OrderConfirmPurchaseEvent event) {
        auctionFacade.applyPurchaseConfirmed(event.auctionId());
    }

    @KafkaListener(topics = ORDER_REFUND_ACCEPTED, groupId = "auction")
    public void handleOrderRefundAccepted(OrderRefundAcceptedEvent event) {
        auctionFacade.applyRefundAccepted(event.auctionId());
    }

    private LocalDateTime resolveLegacyEndAt(ProductRegisteredEvent event, LocalDateTime startAt) {
        if (event.customEndAt() != null) {
            return event.customEndAt();
        }
        return startAt.plusDays(event.duration());
    }

    // null이면 영국식
    private AuctionType resolveType(ProductRegisteredEvent event) {
        if (event.auctionType() == null) {
            return AuctionType.ENGLISH;
        }
        try {
            return AuctionType.valueOf(event.auctionType());
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("알 수 없는 경매 유형입니다. productId="
                    + event.productId() + ", auctionType=" + event.auctionType());
        }
    }

    // 네덜란드식은 공지 시각과 조건이 모두 있어야 한다
    private ProductRegisteredEvent.DutchTerms requireDutch(ProductRegisteredEvent event) {
        ProductRegisteredEvent.DutchTerms dutch = event.dutch();
        if (event.startPrice() == null || event.scheduledAt() == null || dutch == null
                || dutch.floorPrice() == null || dutch.dropRatePercent() == null || dutch.dropIntervalSec() == null) {
            throw new InvalidEventException("네덜란드식 조건이 없습니다. productId=" + event.productId());
        }
        return dutch;
    }


    private AuctionCreateDto toCreateDto(ProductRegisteredEvent event, LocalDateTime now) {
        if (resolveType(event) == AuctionType.DUTCH) {
            ProductRegisteredEvent.DutchTerms dutch = requireDutch(event);
            return AuctionCreateDto.dutch(event.productId(), event.sellerId(), event.startPrice(),
                    event.shippingFee(), event.scheduledAt(), event.category(),
                    dutch.floorPrice(), dutch.dropRatePercent(), dutch.dropIntervalSec());
        }

        LocalDateTime startAt = event.scheduledAt() != null ? event.scheduledAt() : now;
        ProductRegisteredEvent.EnglishTerms english = event.english();
        return AuctionCreateDto.english(event.productId(), event.sellerId(), event.startPrice(),
                event.shippingFee(), startAt, event.category(),
                english != null ? english.instantWinPrice() : event.instantWinPrice(),
                english != null ? english.endAt() : resolveLegacyEndAt(event, startAt));
    }

}
