package com.jjinmak.auction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {"com.jjinmak.auction", "com.jjinmak.common"})
@EnableJpaAuditing
public class AuctionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuctionApplication.class, args);
    }
}
