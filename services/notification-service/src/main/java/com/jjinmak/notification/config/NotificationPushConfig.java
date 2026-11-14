package com.jjinmak.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
@EnableConfigurationProperties(NotificationPushProperties.class)
public class NotificationPushConfig {

    public static final String PUSH_EXECUTOR = "pushExecutor";

    @Bean(name = PUSH_EXECUTOR)
    public ThreadPoolTaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("push-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("푸시 대기열이 가득 차 발송을 건너뜁니다. 알림함에는 저장되어 있습니다."));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
