package com.jjinmak.recommend.domain;

import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
public enum InteractionType {
    WIN(1.0),
    BID(0.6),
    WISH(0.4);

    private static final double HALF_LIFE_DAYS = 14;

    private final double weight;

    InteractionType(double weight){
        this.weight = weight;
    }

    public double weightAt(LocalDateTime occurredAt, LocalDateTime now){
        double days = Math.max(0, Duration.between(occurredAt, now).toSeconds()) / 86400.0;
        return weight * Math.pow(0.5, days / HALF_LIFE_DAYS);
    }
}
