package com.jjinmak.back.payment.support;

import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.PaymentShare;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.WalletType;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 테스트용 홀딩. 대사는 홀딩의 주문 id · 상태 · 금액 · 수수료만 보므로
 * 구매자 · 판매자 지갑은 id 만 다른 빈 지갑으로 채운다. 상태는 실제 도메인 메서드로 바꾼다.
 *
 * 홀딩 금액 = 낙찰가 + 배송비, 정산액 = 홀딩 금액 - 수수료.
 */
public final class HoldingFixture {

    private static final Long PAYMENT_ID = 5_001L;
    private static final long BUYER_WALLET_ID = 1L;
    private static final long SELLER_WALLET_ID = 2L;

    private HoldingFixture() {
    }

    public static Holding held(long orderId, long winningPrice, long deliveryFee, long feeAmount) {
        return Holding.hold(orderId, PAYMENT_ID, wallet(BUYER_WALLET_ID), wallet(SELLER_WALLET_ID),
                winningPrice, deliveryFee, feeAmount);
    }

    // 금액 구성(포인트 · 예치금 · 직접 결제)을 지정한 홀딩
    public static Holding held(long orderId, long winningPrice, long deliveryFee, long feeAmount, PaymentShare share) {
        return Holding.hold(orderId, PAYMENT_ID, wallet(BUYER_WALLET_ID), wallet(SELLER_WALLET_ID),
                winningPrice, deliveryFee, feeAmount, share);
    }

    public static Holding refunded(long orderId, long winningPrice, long deliveryFee, long feeAmount, PaymentShare share) {
        Holding holding = held(orderId, winningPrice, deliveryFee, feeAmount, share);
        holding.refund(holding.getAmount());
        return holding;
    }

    public static Holding confirmed(long orderId, long winningPrice, long deliveryFee, long feeAmount) {
        Holding holding = held(orderId, winningPrice, deliveryFee, feeAmount);
        holding.confirm();
        return holding;
    }

    public static Holding released(long orderId, long winningPrice, long deliveryFee, long feeAmount) {
        Holding holding = confirmed(orderId, winningPrice, deliveryFee, feeAmount);
        holding.release();
        return holding;
    }

    public static Holding refunded(long orderId, long winningPrice, long deliveryFee, long feeAmount) {
        Holding holding = held(orderId, winningPrice, deliveryFee, feeAmount);
        holding.refund(holding.getAmount());
        return holding;
    }

    private static Wallet wallet(long id) {
        Wallet wallet = new Wallet(null, WalletType.USER);
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }
}
