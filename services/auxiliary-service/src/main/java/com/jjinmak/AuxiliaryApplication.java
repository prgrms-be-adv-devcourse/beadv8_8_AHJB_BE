package com.jjinmak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// 배포 단위 auxiliary-service: chat · notification · delivery 세 컨텍스트를 한 프로세스로 띄운다
@SpringBootApplication(scanBasePackages = {
        "com.jjinmak.boundedContext",   // chat, notification, delivery
        "com.jjinmak.global",           // 배포 단위 공용 설정 (SecurityConfig)
        "com.jjinmak.common"            // GlobalExceptionHandler, Kafka 공통 설정, 인증 핸들러
})
@EntityScan("com.jjinmak.boundedContext")
@EnableJpaRepositories("com.jjinmak.boundedContext")
@EnableJpaAuditing
public class AuxiliaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuxiliaryApplication.class, args);
    }
}