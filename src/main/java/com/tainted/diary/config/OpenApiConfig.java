package com.tainted.diary.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI diaryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("diary-service API")
                .version("0.1.0")
                .description("일기 CRUD, 서버측 envelope 암호화/복호화, diary.created 이벤트 발행"));
    }
}
