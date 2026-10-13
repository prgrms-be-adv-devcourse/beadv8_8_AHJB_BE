package com.jjinmak.back.boundedContext.wallet;

import com.jjinmak.back.boundedContext.wallet.app.WalletFacade;
import com.jjinmak.back.boundedContext.wallet.domain.Holding;
import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import com.jjinmak.back.boundedContext.wallet.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.wallet.domain.enums.HoldingStatus;
import com.jjinmak.back.boundedContext.wallet.domain.enums.ReferenceType;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import com.jjinmak.back.boundedContext.wallet.out.HoldingRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletTransactionRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.ErrorCode;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import com.jjinmak.back.shared.wallet.dto.HoldDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주문 대금 홀딩 (WalletFacade.hold) 과 잔액 조회 (WalletFacade.getBalance) 검증.
 *
 * 구매자 1명, 판매자 2명. 구매자는 30,000원을 충전해둔 상태에서 시작한다.
 * 주문 A: 판매자1, 낙찰가 10,000 + 배송비 2,000 = 12,000 (수수료 1,000)
 * 주문 B: 판매자2, 낙찰가  5,000 + 배송비 1,000 =  6,000 (수수료   500)
 * 총액 18,000
 *
 * ESCROW 는 다른 테스트와 공유되는 시스템 지갑이라 절대값이 아니라 증감량으로 검증한다.
 */
@SpringBootTest
class WalletHoldTest {

    private static final long CHARGE_AMOUNT = 30_000L;

    @Autowired private WalletFacade walletFacade;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletMemberRepository walletMemberRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private HoldingRepository holdingRepository;

    private Long buyerId, seller1Id, seller2Id;
    private Long buyerWalletId, seller1WalletId, seller2WalletId, escrowWalletId;
    private Long paymentId, orderAId, orderBId;
    private long escrowBefore;

    @BeforeEach
    void setUp() {
        buyerId   = randomId();
        seller1Id = randomId();
        seller2Id = randomId();
        paymentId = randomId();
        orderAId  = randomId();
        orderBId  = randomId();

        buyerWalletId   = createUserWallet(buyerId);
        seller1WalletId = createUserWallet(seller1Id);
        seller2WalletId = createUserWallet(seller2Id);
        escrowWalletId  = walletRepository.findByTypeAndHolderIsNull(WalletType.ESCROW).orElseThrow().getId();

        walletFacade.charge(new ChargeDto(buyerId, CHARGE_AMOUNT, paymentId));
        escrowBefore = balanceOf(escrowWalletId);
    }

    @AfterEach
    void tearDown() {
        holdingRepository.deleteAll(holdingsOfPayment());
        walletTransactionRepository.deleteAll(transactionsOfThisTest());
        walletRepository.deleteById(buyerWalletId);
        walletRepository.deleteById(seller1WalletId);
        walletRepository.deleteById(seller2WalletId);
        walletMemberRepository.deleteById(buyerId);
        walletMemberRepository.deleteById(seller1Id);
        walletMemberRepository.deleteById(seller2Id);
    }

    // ===== 성공 =====

    @Test
    @DisplayName("홀딩 성공: 구매자 잔액 감소, ESCROW 증가, 주문별 Holding 생성, 원장 주문별 2줄씩")
    void hold_success() {
        walletFacade.hold(twoOrders());

        // 잔액
        assertThat(balanceOf(buyerWalletId)).isEqualTo(CHARGE_AMOUNT - 18_000L);
        assertThat(balanceOf(escrowWalletId) - escrowBefore).isEqualTo(18_000L);
        assertThat(balanceOf(seller1WalletId)).isZero();   // 판매자는 아직 못 받는다
        assertThat(balanceOf(seller2WalletId)).isZero();

        // Holding
        List<Holding> holdings = holdingsOfPayment();
        assertThat(holdings).hasSize(2);

        Holding a = holdingOf(orderAId);
        assertThat(a.getStatus()).isEqualTo(HoldingStatus.HELD);
        assertThat(a.getWinningPrice()).isEqualTo(10_000L);
        assertThat(a.getDeliveryFee()).isEqualTo(2_000L);
        assertThat(a.getAmount()).isEqualTo(12_000L);
        assertThat(a.getFeeAmount()).isEqualTo(1_000L);          // 낙찰가의 10%, 배송비 제외
        assertThat(a.getSettlementAmount()).isEqualTo(11_000L);
        assertThat(a.getSellerWallet().getId()).isEqualTo(seller1WalletId);
        assertThat(a.getBuyerWallet().getId()).isEqualTo(buyerWalletId);

        Holding b = holdingOf(orderBId);
        assertThat(b.getAmount()).isEqualTo(6_000L);
        assertThat(b.getFeeAmount()).isEqualTo(500L);
        assertThat(b.getSellerWallet().getId()).isEqualTo(seller2WalletId);
    }

    @Test
    @DisplayName("원장: 같은 transactionGroupId 로 묶이고, 주문(ORDER)을 가리키고, 합이 0이다")
    void hold_ledgerIsBalancedAndGrouped() {
        walletFacade.hold(twoOrders());

        List<WalletTransaction> txs = transactionsOfThisTest().stream()
                .filter(tx -> tx.getType() == WalletTransactionType.HOLD_PAY
                        || tx.getType() == WalletTransactionType.HOLD_RECEIVE)
                .toList();

        assertThat(txs).hasSize(4);
        assertThat(txs).extracting(WalletTransaction::getTransactionGroupId).containsOnly(txs.get(0).getTransactionGroupId());
        assertThat(txs).extracting(WalletTransaction::getReferenceType).containsOnly(ReferenceType.ORDER);
        assertThat(txs).extracting(WalletTransaction::getReferenceId).containsExactlyInAnyOrder(orderAId, orderAId, orderBId, orderBId);
        assertThat(txs.stream().mapToLong(WalletTransaction::getAmount).sum()).isZero();

        // 구매자 쪽 balanceAfter 가 줄마다 정확히 내려간다 (30,000 → 18,000 → 12,000 순서는 items 순)
        List<Long> buyerAfter = txs.stream()
                .filter(tx -> tx.getWallet().getId().equals(buyerWalletId))
                .sorted((x, y) -> x.getId().compareTo(y.getId()))
                .map(WalletTransaction::getBalanceAfter)
                .toList();
        assertThat(buyerAfter).containsExactly(18_000L, 12_000L);
    }

    @Test
    @DisplayName("같은 paymentId 로 두 번 홀딩해도 한 번만 반영된다 (멱등)")
    void hold_isIdempotent() {
        walletFacade.hold(twoOrders());
        walletFacade.hold(twoOrders());

        assertThat(balanceOf(buyerWalletId)).isEqualTo(CHARGE_AMOUNT - 18_000L);
        assertThat(holdingsOfPayment()).hasSize(2);
    }

    // ===== 실패 =====

    @Test
    @DisplayName("잔액 부족이면 INSUFFICIENT_BALANCE 이고 아무것도 변하지 않는다")
    void hold_insufficientBalance_fails() {
        HoldDto dto = new HoldDto(paymentId, buyerId, 40_000L, List.of(
                new HoldDto.Item(orderAId, seller1Id, 38_000L, 2_000L)));

        assertFails(() -> walletFacade.hold(dto), INSUFFICIENT_BALANCE);
        assertNothingHeld();
    }

    @Test
    @DisplayName("주문 내역이 비어 있으면 HOLD_ITEMS_EMPTY")
    void hold_emptyItems_fails() {
        assertFails(() -> walletFacade.hold(new HoldDto(paymentId, buyerId, 0L, List.of())), HOLD_ITEMS_EMPTY);
        assertFails(() -> walletFacade.hold(new HoldDto(paymentId, buyerId, 0L, null)),      HOLD_ITEMS_EMPTY);
        assertNothingHeld();
    }

    @Test
    @DisplayName("주문 금액 합이 총액과 다르면 HOLD_AMOUNT_MISMATCH")
    void hold_totalMismatch_fails() {
        HoldDto dto = new HoldDto(paymentId, buyerId, 18_001L, twoOrders().items());

        assertFails(() -> walletFacade.hold(dto), HOLD_AMOUNT_MISMATCH);
        assertNothingHeld();
    }

    @Test
    @DisplayName("구매자 지갑이 없으면 WALLET_NOT_FOUND")
    void hold_unknownBuyer_fails() {
        HoldDto dto = new HoldDto(paymentId, randomId(), 18_000L, twoOrders().items());

        assertFails(() -> walletFacade.hold(dto), WALLET_NOT_FOUND);
        assertNothingHeld();
    }

    @Test
    @DisplayName("두 번째 주문의 판매자 지갑이 없으면 첫 번째 주문까지 전부 롤백된다")
    void hold_unknownSellerInSecondItem_rollsBackAll() {
        HoldDto dto = new HoldDto(paymentId, buyerId, 18_000L, List.of(
                new HoldDto.Item(orderAId, seller1Id,  10_000L, 2_000L),
                new HoldDto.Item(orderBId, randomId(),  5_000L, 1_000L)));   // 없는 판매자

        assertFails(() -> walletFacade.hold(dto), WALLET_NOT_FOUND);
        assertNothingHeld();   // 주문 A 도 남아 있으면 안 된다
    }

    // ===== 잔액 조회 =====

    @Test
    @DisplayName("getBalance 는 현재 잔액을 돌려준다")
    void getBalance_success() {
        assertThat(walletFacade.getBalance(buyerId)).isEqualTo(CHARGE_AMOUNT);

        walletFacade.hold(twoOrders());
        assertThat(walletFacade.getBalance(buyerId)).isEqualTo(CHARGE_AMOUNT - 18_000L);
    }

    @Test
    @DisplayName("지갑이 없는 holderId 로 getBalance 하면 WALLET_NOT_FOUND")
    void getBalance_unknownHolder_fails() {
        assertFails(() -> walletFacade.getBalance(randomId()), WALLET_NOT_FOUND);
    }

    // ===== helpers =====

    private HoldDto twoOrders() {
        return new HoldDto(paymentId, buyerId, 18_000L, List.of(
                new HoldDto.Item(orderAId, seller1Id, 10_000L, 2_000L),
                new HoldDto.Item(orderBId, seller2Id,  5_000L, 1_000L)));
    }

    private void assertNothingHeld() {
        assertThat(balanceOf(buyerWalletId)).isEqualTo(CHARGE_AMOUNT);
        assertThat(balanceOf(escrowWalletId)).isEqualTo(escrowBefore);
        assertThat(holdingsOfPayment()).isEmpty();
        assertThat(transactionsOfThisTest())
                .extracting(WalletTransaction::getType)
                .containsOnly(WalletTransactionType.CHARGE);   // setUp 의 충전 한 줄뿐
    }

    private void assertFails(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private Long createUserWallet(Long memberId) {
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        return walletRepository.save(Wallet.createUserWallet(member)).getId();
    }

    private long balanceOf(Long walletId) {
        return walletRepository.findById(walletId).orElseThrow().getBalance();
    }

    private Holding holdingOf(Long orderId) {
        return holdingsOfPayment().stream()
                .filter(h -> h.getOrderId().equals(orderId))
                .findFirst().orElseThrow();
    }

    private List<Holding> holdingsOfPayment() {
        return holdingRepository.findAll().stream()
                .filter(h -> paymentId.equals(h.getPaymentId()))
                .toList();
    }

    /** 이 테스트가 만든 원장만: 구매자/판매자 지갑 것 전부 + ESCROW 지갑 것 중 이 테스트 주문을 가리키는 것 */
    private List<WalletTransaction> transactionsOfThisTest() {
        Set<Long> myWallets = Set.of(buyerWalletId, seller1WalletId, seller2WalletId);
        Set<Long> myOrders  = Set.of(orderAId, orderBId);
        return walletTransactionRepository.findAll().stream()
                .filter(tx -> myWallets.contains(tx.getWallet().getId())
                        || (tx.getWallet().getId().equals(escrowWalletId) && myOrders.contains(tx.getReferenceId())))
                .toList();
    }

    private static Long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }
}