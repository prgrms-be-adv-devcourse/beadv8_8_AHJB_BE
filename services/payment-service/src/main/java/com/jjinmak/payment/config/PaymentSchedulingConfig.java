package com.jjinmak.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 결제 서비스 스케줄러 활성화.
 * PaymentScheduler(충전 복구), TossWebhookScheduler(웹훅 처리)의 @Scheduled 는 이 설정이 있어야 실행된다.
 */
@Configuration
@EnableScheduling
public class PaymentSchedulingConfig {
}
