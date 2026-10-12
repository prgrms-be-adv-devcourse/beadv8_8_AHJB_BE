package com.jjinmak.back.boundedContext.delivery.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import static com.jjinmak.back.boundedContext.delivery.exception.DeliveryErrorCode.DELIVERY_FORBIDDEN;

@Entity
@Getter
@NoArgsConstructor
public class Delivery extends BaseIdAndTime {

    @NotNull
    @Column(unique = true)
    Long orderId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    DeliveryMember winner;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    DeliveryMember seller;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    CarrierType carrier;

    @NotNull
    String waybill;

    public Delivery(Long orderId, DeliveryMember winner, DeliveryMember seller, CarrierType carrier, String waybill){
        this.orderId = orderId;
        this.winner = winner;
        this.seller = seller;
        this.carrier = carrier;
        this.waybill = waybill;
    }

    public void update(CarrierType carrier, String waybill){
        this.carrier = carrier;
        this.waybill = waybill;
    }

    public void validateSeller(DeliveryMember member){
        if (!this.seller.getId().equals(member.getId()))
            throw new BusinessException(DELIVERY_FORBIDDEN);
    }
}
