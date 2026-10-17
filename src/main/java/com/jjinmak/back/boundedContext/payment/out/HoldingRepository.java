package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.payment.domain.Holding;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.enums.HoldingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    boolean existsByPaymentId(Long paymentId);

    Optional<Holding> findByOrderId(Long orderId);

    // ===== 구매자 관점 =====

    Page<Holding> findByBuyerWallet(Wallet buyerWallet, Pageable pageable);

    Page<Holding> findByBuyerWalletAndStatus(Wallet buyerWallet, HoldingStatus status, Pageable pageable);

    /** 구매자 돈이 묶여 있는 총액 (amount 합) */
    @Query("select coalesce(sum(h.amount), 0) from Holding h where h.buyerWallet = :wallet and h.status in :statuses")
    long sumAmountByBuyerWalletAndStatusIn(@Param("wallet") Wallet wallet, @Param("statuses") Collection<HoldingStatus> statuses);

    // ===== 판매자 관점 =====

    Page<Holding> findBySellerWallet(Wallet sellerWallet, Pageable pageable);

    Page<Holding> findBySellerWalletAndStatus(Wallet sellerWallet, HoldingStatus status, Pageable pageable);

    /** 판매자가 받을 정산액 합 (amount − feeAmount) */
    @Query("select coalesce(sum(h.amount - h.feeAmount), 0) from Holding h where h.sellerWallet = :wallet and h.status in :statuses")
    long sumSettlementBySellerWalletAndStatusIn(@Param("wallet") Wallet wallet, @Param("statuses") Collection<HoldingStatus> statuses);
}
