package com.jjinmak.back.boundedContext.order.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
public class OrderGroup extends BaseIdAndTime {

    @NotNull
    Long totalPrice;

    @OneToMany(mappedBy = "group")
    List<Order> orders = new ArrayList<>();

    public OrderGroup(Long totalPrice){
        this.totalPrice = totalPrice;
    }

    public void add(Order order){
        this.orders.add(order);
    }
}
