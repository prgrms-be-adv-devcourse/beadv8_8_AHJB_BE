package com.jjinmak.back.boundedContext.order.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
public class OrderGroup extends BaseIdAndTime {

    @NotNull
    Long totalPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    OrderMember winner;

    @OneToMany(mappedBy = "group", cascade = CascadeType.REMOVE)
    List<Order> orders = new ArrayList<>();

    LocalDateTime lifeTime;

    public OrderGroup(OrderMember winner, Long totalPrice){
        this.winner = winner;
        this.totalPrice = totalPrice;
        this.lifeTime = LocalDateTime.now().plusHours(1);
    }

    public void add(Order order){
        this.orders.add(order);
    }

    public void updateLifeTime(){
        this.lifeTime = LocalDateTime.now().plusHours(1);
    }
}
