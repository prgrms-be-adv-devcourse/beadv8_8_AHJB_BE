package com.jjinmak.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

// 검색 서비스는 DB 없이 Elasticsearch만 쓴다.
// common 라이브러리가 JPA를 함께 가져오므로, DB 관련 자동 설정을 끈다.
@SpringBootApplication(
        scanBasePackages = {"com.jjinmak.search", "com.jjinmak.common"},
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                DataJpaRepositoriesAutoConfiguration.class
        }
)
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
