package com.jjinmak.product.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 경매 등록 요청 스케줄러, 미사용 이미지 정리 스케줄러를 켠다.
@Configuration
@EnableScheduling
public class ProductSchedulingConfig {
}
