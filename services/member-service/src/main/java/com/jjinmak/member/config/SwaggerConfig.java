package com.jjinmak.member.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@RequiredArgsConstructor
public class SwaggerConfig {

    private final Environment environment;

    @Bean
    public OpenAPI openAPI() {
        String jwt = "JWT";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwt);

        Components components = new Components()
                .addSecuritySchemes(jwt, new SecurityScheme()
                        .name(jwt)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));


        return new OpenAPI()
                .components(components)
                .addSecurityItem(securityRequirement)
                .info(apiInfo());
    }

    private Info apiInfo() {
        String profile = String.join(",", environment.getActiveProfiles());
        return new Info()
                .title("jjinmak "+profile + " API")
                .description("""
                # 게임 전문 이커머스 플랫폼 jjinmak Member Service.

                ## 응답 형식
                - Restful을 준수하고 있습니다.
                - 시스템 시간은 **한국 시간(Asia/Soule) 기준입니다**

                ## API 담당자

                유저/채팅, 채팅 API
                - 팀 안현잘부 팀원 차승환
               """)
                .version("1.0.0");
    }
}
