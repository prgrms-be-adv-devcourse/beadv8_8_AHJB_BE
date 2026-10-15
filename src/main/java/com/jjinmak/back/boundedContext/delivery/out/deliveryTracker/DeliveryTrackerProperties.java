package com.jjinmak.back.boundedContext.delivery.out.deliveryTracker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "delivery-tracker")
public record DeliveryTrackerProperties(
        @DefaultValue("https://apis.tracker.delivery/graphql")
        String baseUrl,
        @DefaultValue
        RateLimit rateLimit,

        @DefaultValue("asdf")
        String clientId,
        @DefaultValue("qwer")
        String clientSecret

) {
    public record RateLimit(
            @DefaultValue("10")
            long capacity,
            @DefaultValue("10")
            long refillTokens,
            @DefaultValue("1s")
            Duration refillPeriod,
            @DefaultValue("10s")
            Duration maxWait
    ){}
}
