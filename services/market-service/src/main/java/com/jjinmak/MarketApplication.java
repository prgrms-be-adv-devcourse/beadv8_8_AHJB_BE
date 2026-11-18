package com.jjinmak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.jjinmak.global",
        "com.jjinmak.boundedcontext",
        "com.jjinmak.common"
})
@EntityScan(basePackages = "com.jjinmak.boundedcontext")
@EnableJpaRepositories(basePackages = "com.jjinmak.boundedcontext")
@EnableJpaAuditing
public class MarketApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketApplication.class, args);
    }
}