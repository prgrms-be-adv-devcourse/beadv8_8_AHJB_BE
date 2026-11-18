package com.jjinmak.boundedcontext.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.push")
public record NotificationPushProperties(String webBaseUrl, Fcm fcm) {

    public record Fcm(boolean enabled, String credentialsBase64) {}
}