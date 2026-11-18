package com.jjinmak.back.order.domain;

import com.jjinmak.common.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
public class OrderWonAuction extends BaseManualIdAndTime {

    public OrderWonAuction(Long auctionId){
        super(auctionId);
    }
}
