package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.Holding;
import com.jjinmak.payment.domain.Wallet;
import com.jjinmak.payment.domain.enums.HoldingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    boolean existsByPaymentId(Long paymentId);

    Optional<Holding> findByOrderId(Long orderId);

    // ===== 구매자 관점 =====

    Page<Holding> findByBuyerWallet(Wallet buyerWallet, Pageable pageable);

    Page<Holding> findByBuyerWalletAndStatus(Wallet buyerWallet, HoldingStatus status, Pageable pageable);

    @Query("select coalesce(sum(h.amount), 0) from Holding h where h.buyerWallet = :wallet and h.status in :statuses")
    long sumAmountByBuyerWalletAndStatusIn(@Param("wallet") Wallet wallet, @Param("statuses") Collection<HoldingStatus> statuses);

    // ===== 판매자 관점 =====

    Page<Holding> findBySellerWallet(Wallet sellerWallet, Pageable pageable);

    Page<Holding> findBySellerWalletAndStatus(Wallet sellerWallet, HoldingStatus status, Pageable pageable);

    @Query("select coalesce(sum(h.amount - h.feeAmount), 0) from Holding h where h.sellerWallet = :wallet and h.status in :statuses")
    long sumSettlementBySellerWalletAndStatusIn(@Param("wallet") Wallet wallet, @Param("statuses") Collection<HoldingStatus> statuses);

    // ===== 대사 =====

    // 해당 상태인 홀딩 전체의 금액 합
    @Query("select coalesce(sum(h.amount), 0) from Holding h where h.status in :statuses")
    long sumAmountByStatusIn(@Param("statuses") Collection<HoldingStatus> statuses);

    // 기간 안에 만들어졌거나 환불 · 정산된 홀딩의 주문 id (startAt 이상, endAt 미만)
    @Query("select h.orderId from Holding h "
            + "where (h.createdAt >= :startAt and h.createdAt < :endAt) "
            + "or (h.refundedAt >= :startAt and h.refundedAt < :endAt) "
            + "or (h.releasedAt >= :startAt and h.releasedAt < :endAt)")
    List<Long> findOrderIdsChangedBetween(@Param("startAt") LocalDateTime startAt,
                                          @Param("endAt") LocalDateTime endAt);

    List<Holding> findByOrderIdIn(Collection<Long> orderIds);
}
