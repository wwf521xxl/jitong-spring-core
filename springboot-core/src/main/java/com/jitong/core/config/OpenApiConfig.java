package com.jitong.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jitongOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("计件通 Spring Boot 核心模块 API")
                        .version("1.0.0")
                        .description("计件通生产 API；支持 Bearer JWT 与 HttpOnly Cookie。"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("jitong_session")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addSecurityItem(new SecurityRequirement().addList("cookieAuth"));
    }
}
