package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Holding;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.boundedContext.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.boundedContext.payment.out.HoldingRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletRepository;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.event.WalletReleasedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.SETTLEMENT_AMOUNT_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정산 요청(SettlementRequestedEvent) → 홀딩 릴리즈. 리스너와 같은 진입점(WalletFacade.releaseHolding) 으로 검증.
 * 홀딩: 주문 61, 낙찰 10_000 + 배송비 0, 수수료 1_000 → 정산액 9_000, CONFIRMED
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class SettlementReleaseTest {

    private static final Long BUYER_ID = 9_601L;
    private static final Long SELLER_ID = 9_602L;
    private static final Long ORDER_ID = 61L;

    @Autowired WalletFacade walletFacade;
    @Autowired SystemWalletFinder finder;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired HoldingRepository holdingRepository;
    @Autowired ApplicationEvents events;

    @MockitoBean SettlementEventListener settlementEventListener;

    private Wallet sellerWallet;
    private Holding holding;
    private long escrowBefore;
    private long feeBefore;

    @BeforeEach
    void setUp() {
        Wallet buyerWallet = walletOf(BUYER_ID);
        sellerWallet = walletOf(SELLER_ID);

        Wallet escrow = finder.escrow();
        escrow.deposit(10_000L);                                   // 결제 때 ESCROW 로 들어온 돈
        escrowBefore = escrow.getBalance();
        feeBefore = finder.fee().getBalance();

        holding = Holding.hold(ORDER_ID, 7_777L, buyerWallet, sellerWallet, 10_000L, 0L, 1_000L);
        holding.confirm();
        holding = holdingRepository.save(holding);
    }

    private Wallet walletOf(Long memberId) {
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        return walletRepository.save(Wallet.createUserWallet(member));
    }

    @Test
    @DisplayName("정산 요청 금액이 홀딩과 일치하면 ESCROW → 판매자 정산금·FEE 로 옮기고 회신 이벤트를 발행한다")
    void release_success() {
        walletFacade.releaseHolding(ORDER_ID, 9_000L, 1_000L);

        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.RELEASED);
        assertThat(sellerWallet.getSettlementBalance()).isEqualTo(9_000L);
        assertThat(sellerWallet.getBalance()).isZero();                         // 예치금에는 안 섞임
        assertThat(finder.escrow().getBalance()).isEqualTo(escrowBefore - 10_000L);
        assertThat(finder.fee().getBalance()).isEqualTo(feeBefore + 1_000L);

        List<WalletReleasedEvent> published = events.stream(WalletReleasedEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().orderId()).isEqualTo(ORDER_ID);
        assertThat(published.getFirst().sellerId()).isEqualTo(SELLER_ID);
        assertThat(published.getFirst().settlementAmount()).isEqualTo(9_000L);
        assertThat(published.getFirst().feeAmount()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("정산 요청 금액이 홀딩과 다르면 돈을 옮기지 않고 WALLET312")
    void release_amountMismatch() {
        assertThatThrownBy(() -> walletFacade.releaseHolding(ORDER_ID, 9_500L, 500L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(SETTLEMENT_AMOUNT_MISMATCH);

        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.CONFIRMED);
        assertThat(sellerWallet.getSettlementBalance()).isZero();
        assertThat(finder.escrow().getBalance()).isEqualTo(escrowBefore);
        assertThat(events.stream(WalletReleasedEvent.class).count()).isZero();
    }

    @Test
    @DisplayName("이미 릴리즈된 홀딩에 다시 요청이 오면 돈은 안 움직이고 회신만 다시 보낸다 (멱등)")
    void release_idempotent() {
        walletFacade.releaseHolding(ORDER_ID, 9_000L, 1_000L);
        walletFacade.releaseHolding(ORDER_ID, 9_000L, 1_000L);

        assertThat(sellerWallet.getSettlementBalance()).isEqualTo(9_000L);
        assertThat(finder.escrow().getBalance()).isEqualTo(escrowBefore - 10_000L);
        assertThat(events.stream(WalletReleasedEvent.class).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("구매 확정 전(HELD) 홀딩은 정산할 수 없다")
    void release_notConfirmed() {
        Holding held = holdingRepository.save(
                Holding.hold(62L, 7_778L, walletRepository.findById(holding.getBuyerWallet().getId()).orElseThrow(),
                        sellerWallet, 5_000L, 0L, 500L));

        assertThatThrownBy(() -> walletFacade.releaseHolding(62L, 4_500L, 500L))
                .isInstanceOf(BusinessException.class);

        assertThat(held.getStatus()).isEqualTo(HoldingStatus.HELD);
        assertThat(sellerWallet.getSettlementBalance()).isZero();
    }
}
