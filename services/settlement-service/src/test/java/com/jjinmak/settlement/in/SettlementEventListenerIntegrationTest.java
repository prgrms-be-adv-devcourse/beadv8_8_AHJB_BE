package com.jjinmak.settlement.in;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import com.jjinmak.settlement.domain.Settlement;
import com.jjinmak.settlement.domain.SettlementProduct;
import com.jjinmak.settlement.out.SettlementProductRepository;
import com.jjinmak.settlement.out.SettlementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.jjinmak.contracts.settlement.domain.SettlementStatus.COMPLETED;
import static com.jjinmak.contracts.settlement.domain.SettlementStatus.PENDING;
import static com.jjinmak.settlement.exception.SettlementErrorCode.PRODUCT_NOT_FOUND;
import static com.jjinmak.settlement.exception.SettlementErrorCode.SETTLEMENT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class SettlementEventListenerIntegrationTest {

    static final Long ORDER_ID = 1L;
    static final Long WINNER_ID = 10L;
    static final Long SELLER_ID = 20L;
    static final Long AUCTION_ID = 30L;
    static final Long PRODUCT_ID = 100L;
    static final String PRODUCT_NAME = "게임보이 컬러";
    static final Long WINNING_PRICE = 50_000L;
    static final Long DELIVERY_FEE = 3_000L;
    static final Long FEE_AMOUNT = 5_000L;          // 낙찰가의 10%
    static final Long SETTLEMENT_AMOUNT = 48_000L;  // 낙찰가 - 수수료 + 배송비
    static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 11, 10, 12, 0);
    static final LocalDate PAYOUT_DATE = LocalDate.of(2026, 12, 15);  // 구매확정한 달의 다음 달 15일
    static final LocalDateTime RELEASED_AT = LocalDateTime.of(2026, 12, 15, 3, 0);

    @Autowired
    SettlementEventListener settlementEventListener;

    @Autowired
    SettlementRepository settlementRepository;

    @Autowired
    SettlementProductRepository settlementProductRepository;

    @AfterEach
    void tearDown() {
        settlementRepository.deleteAllInBatch();
        settlementProductRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("상품 등록 이벤트를 받으면 상품 id를 그대로 PK로 쓰는 상품명 복사본이 생긴다")
    void productRegistered_savesProductCopy() {
        settlementEventListener.handle(productRegistered());

        assertThat(settlementProductRepository.findAll())
                .extracting(SettlementProduct::getId, SettlementProduct::getName)
                .containsExactly(tuple(PRODUCT_ID, PRODUCT_NAME));
    }

    @Test
    @DisplayName("같은 상품 등록 이벤트가 두 번 와도 복사본은 한 건만 생긴다")
    void productRegistered_duplicated_savesOnlyOne() {
        settlementEventListener.handle(productRegistered());
        settlementEventListener.handle(productRegistered());

        assertThat(settlementProductRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("구매확정 이벤트를 받으면 수수료·정산 금액·지급일이 계산된 PENDING 정산서가 생긴다")
    void confirmPurchase_createsPendingSettlement() {
        settlementEventListener.handle(productRegistered());

        settlementEventListener.handle(confirmPurchase());

        assertThat(settlementRepository.findAll())
                .extracting(
                        Settlement::getOrderId,
                        Settlement::getSellerId,
                        Settlement::getProductName,
                        Settlement::getFeeAmount,
                        Settlement::getSettlementAmount,
                        Settlement::getPayoutDate,
                        Settlement::getStatus)
                .containsExactly(tuple(ORDER_ID, SELLER_ID, PRODUCT_NAME, FEE_AMOUNT, SETTLEMENT_AMOUNT, PAYOUT_DATE, PENDING));
    }

    @Test
    @DisplayName("같은 구매확정 이벤트가 두 번 와도 정산서는 한 건만 생긴다")
    void confirmPurchase_duplicated_createsOnlyOne() {
        settlementEventListener.handle(productRegistered());

        settlementEventListener.handle(confirmPurchase());
        settlementEventListener.handle(confirmPurchase());

        assertThat(settlementRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("상품명 복사본보다 구매확정 이벤트를 먼저 받으면 PRODUCT_NOT_FOUND를 던지고 정산서가 생기지 않는다")
    void confirmPurchase_beforeProductCopy_throws() {
        assertThatThrownBy(() -> settlementEventListener.handle(confirmPurchase()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PRODUCT_NOT_FOUND);

        assertThat(settlementRepository.count()).isZero();
    }

    @Test
    @DisplayName("지갑 지급 완료 이벤트를 받으면 정산서가 지급 시각과 함께 COMPLETED가 된다")
    void walletReleased_completesSettlement() {
        pendingSettlement();

        settlementEventListener.handle(walletReleased(RELEASED_AT));

        Settlement settlement = settlementRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertThat(settlement.getStatus()).isEqualTo(COMPLETED);
        assertThat(settlement.getCompletedAt()).isEqualTo(RELEASED_AT);
    }

    @Test
    @DisplayName("같은 지급 완료 이벤트가 다시 와도 예외 없이 처음 완료 시각 그대로 COMPLETED로 남는다")
    void walletReleased_duplicated_keepsFirstCompletion() {
        pendingSettlement();

        settlementEventListener.handle(walletReleased(RELEASED_AT));
        settlementEventListener.handle(walletReleased(RELEASED_AT.plusDays(1)));

        Settlement settlement = settlementRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertThat(settlement.getStatus()).isEqualTo(COMPLETED);
        assertThat(settlement.getCompletedAt()).isEqualTo(RELEASED_AT);
    }

    @Test
    @DisplayName("정산서가 없는 주문의 지급 완료 이벤트는 SETTLEMENT_NOT_FOUND를 던진다 (삼키지 않아야 재시도·DLT로 간다)")
    void walletReleased_unknownOrder_throws() {
        assertThatThrownBy(() -> settlementEventListener.handle(walletReleased(RELEASED_AT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SETTLEMENT_NOT_FOUND);
    }

    private ProductRegisteredEvent productRegistered() {
        return new ProductRegisteredEvent(
                PRODUCT_ID, SELLER_ID, DELIVERY_FEE, 10_000L, null, 3, null, PRODUCT_NAME, "CONSOLE", null);
    }

    private OrderConfirmPurchaseEvent confirmPurchase() {
        return new OrderConfirmPurchaseEvent(
                ORDER_ID, WINNER_ID, SELLER_ID, AUCTION_ID, PRODUCT_ID, WINNING_PRICE, DELIVERY_FEE, CONFIRMED_AT);
    }

    private WalletReleasedEvent walletReleased(LocalDateTime releasedAt) {
        return new WalletReleasedEvent(
                ORDER_ID, SELLER_ID, WINNING_PRICE + DELIVERY_FEE, FEE_AMOUNT, SETTLEMENT_AMOUNT, releasedAt);
    }

    // 상품 등록 → 구매확정 순서로 PENDING 정산서를 만든다
    private void pendingSettlement() {
        settlementEventListener.handle(productRegistered());
        settlementEventListener.handle(confirmPurchase());
    }
}