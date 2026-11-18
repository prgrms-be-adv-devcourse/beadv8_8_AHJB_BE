package com.jjinmak.back.boundedContext.delivery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class DeliveryAsyncConfig {

    public static final String DELIVERY_TASK_EXECUTOR = "deliveryTaskExecutor";

    @Bean(DELIVERY_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor deliveryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1_000);
        executor.setThreadNamePrefix("delivery-async-");

        // 대기열이 가득 차면 예외: 콜백은 2xx가 아니게 되어 Delivery Tracker가 다시 보내고,
        // Webhook 등록과 스케줄러는 로그만 남는다(다음 날 스케줄러가 다시 등록)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
