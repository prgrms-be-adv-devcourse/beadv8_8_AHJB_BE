package com.jjinmak.back.boundedContext.delivery.out;

import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryStatusCode;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderId(Long orderId);

    @Query("select d from Delivery d join fetch d.winner join fetch d.seller where d.orderId=:orderId")
    Optional<Delivery> findByOrderIdWithWinnerAndSeller(Long orderId);

    @Query("select d.id from Delivery d where d.status in :codes")
    List<Long> findIdsByDeliveryStatusIn(Collection<DeliveryStatusCode> codes);

    @Modifying
    @Transactional
    @Query("update Delivery d set d.status = :status where d.id = :deliveryId and d.carrier = :carrier and d.waybill = :waybill")
    void updateStatus(Long deliveryId, CarrierType carrier, String waybill, DeliveryStatusCode status);
}
