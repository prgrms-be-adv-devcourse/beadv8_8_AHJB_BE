package com.jjinmak.back.boundedContext.payment.support;

import com.jjinmak.back.boundedContext.payment.domain.*;
import com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletAccount;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.payment.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 조회 API 테스트용 데이터 세팅. 테스트 트랜잭션 안에서 저장되고 테스트가 끝나면 롤백된다.
 *
 * 회원: 구매자(BUYER, 예치금 20_000), 판매자 A(예치금 3_000 + 정산금 9_000), 판매자 B, 남(OTHER)
 *  - 결제 P1: 구매자, 주문 2건(판매자 A 22_000 / 판매자 B 8_000), 총 30_000, 부족분 18_000 을 토스로 충전(USED) 후 PAID
 *  - 결제 P2: 구매자, 주문 1건 5_000, 전액 예치금 PAID
 *  - 결제 P3: 남의 결제
 *  - 충전 C1: 구매자 수동 10_000 CHARGED (취소 가능)   C2: 구매자 수동 5_000 ABORTED   C3: 남의 수동 충전
 *  - 홀딩 H1: P1/주문10 구매자→판매자A HELD   H2: P1/주문11 구매자→판매자B CONFIRMED   H3: 남→판매자A RELEASED
 *  - 원장(구매자): CHARGE +10_000, HOLD_PAY -22_000 (예치금)
 *  - 원장(판매자A): CHARGE +3_000 (예치금), SETTLEMENT_RECEIVE +9_000 (정산금)
 */
@TestComponent
@RequiredArgsConstructor
public class PaymentApiTestData {

    public static final Long BUYER_ID = 9_001L;
    public static final Long SELLER_A_ID = 9_002L;
    public static final Long SELLER_B_ID = 9_003L;
    public static final Long OTHER_ID = 9_004L;

    public static final Long P1_GROUP = 7_001L;
    public static final Long P2_GROUP = 7_002L;
    public static final Long P3_GROUP = 7_003L;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 10, 0);

    private final WalletMemberRepository walletMemberRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;
    private final HoldingRepository holdingRepository;

    public Wallet buyerWallet, sellerAWallet, sellerBWallet, otherWallet;
    public Payment p1, p2, p3;
    public Charge c1, c2, c3, c1ForP1;
    public Holding h1, h2, h3;

    public void seed() {
        buyerWallet = walletOf(BUYER_ID, 20_000L);
        sellerAWallet = walletOf(SELLER_A_ID, 3_000L);
        sellerAWallet.depositSettlement(9_000L);
        sellerBWallet = walletOf(SELLER_B_ID, 0L);
        otherWallet = walletOf(OTHER_ID, 0L);

        // ----- 결제 -----
        p1 = Payment.create(P1_GROUP, BUYER_ID, buyerWallet, 30_000L);
        p1.addItem(10L, SELLER_A_ID, 20_000L, 2_000L);
        p1.addItem(11L, SELLER_B_ID, 7_000L, 1_000L);
        p1.validateItems();
        p1.decideCharge(12_000L);                       // 부족분 18_000
        p1 = paymentRepository.save(p1);
        c1ForP1 = Charge.requestForPayment(buyerWallet, p1);
        c1ForP1.markCharged("pk_p1", NOW, NOW, "카드", "https://receipt/pk_p1");
        c1ForP1.markUsed(NOW);
        c1ForP1 = chargeRepository.save(c1ForP1);
        p1.markPaid(NOW);

        p2 = Payment.create(P2_GROUP, BUYER_ID, buyerWallet, 5_000L);
        p2.addItem(12L, SELLER_A_ID, 5_000L, 0L);
        p2.validateItems();
        p2.decideCharge(100_000L);                      // 부족분 0
        p2.markPaid(NOW.plusHours(1));
        p2 = paymentRepository.save(p2);

        p3 = Payment.create(P3_GROUP, OTHER_ID, otherWallet, 10_000L);
        p3.addItem(13L, SELLER_A_ID, 10_000L, 0L);
        p3.validateItems();
        p3.markPaid(NOW);
        p3 = paymentRepository.save(p3);

        // ----- 수동 충전 -----
        c1 = Charge.requestManual(buyerWallet, BUYER_ID, 10_000L);
        c1.markCharged("pk_c1", NOW, NOW, "간편결제", "https://receipt/pk_c1");
        c1 = chargeRepository.save(c1);

        c2 = Charge.requestManual(buyerWallet, BUYER_ID, 5_000L);
        c2.markAborted(NOW, "REJECT_CARD_COMPANY", "카드사 거절");
        c2 = chargeRepository.save(c2);

        c3 = Charge.requestManual(otherWallet, OTHER_ID, 7_000L);
        c3.markCharged("pk_c3", NOW, NOW, "카드", null);
        c3 = chargeRepository.save(c3);

        // ----- 홀딩 (수수료 10%) -----
        h1 = holdingRepository.save(Holding.hold(10L, p1.getId(), buyerWallet, sellerAWallet, 20_000L, 2_000L, 2_000L));
        Holding confirmed = Holding.hold(11L, p1.getId(), buyerWallet, sellerBWallet, 7_000L, 1_000L, 700L);
        confirmed.confirm();
        h2 = holdingRepository.save(confirmed);
        Holding released = Holding.hold(13L, p3.getId(), otherWallet, sellerAWallet, 10_000L, 0L, 1_000L);
        released.confirm();
        released.release();
        h3 = holdingRepository.save(released);

        // ----- 원장 -----
        walletTransactionRepository.save(WalletTransaction.of(buyerWallet, WalletTransactionType.CHARGE, 10_000L, "g1", ReferenceType.PAYMENT, c1.getId()));
        walletTransactionRepository.save(WalletTransaction.of(buyerWallet, WalletTransactionType.HOLD_PAY, -22_000L, "g2", ReferenceType.ORDER, 10L));
        walletTransactionRepository.save(WalletTransaction.of(sellerAWallet, WalletTransactionType.CHARGE, 3_000L, "g3", ReferenceType.PAYMENT, c3.getId()));
        walletTransactionRepository.save(WalletTransaction.of(sellerAWallet, WalletAccount.SETTLEMENT, WalletTransactionType.SETTLEMENT_RECEIVE, 9_000L, "g4", ReferenceType.ORDER, 13L));
    }

    private Wallet walletOf(Long memberId, long balance) {
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        Wallet wallet = Wallet.createUserWallet(member);
        if (balance > 0) wallet.deposit(balance);
        return walletRepository.save(wallet);
    }
}
