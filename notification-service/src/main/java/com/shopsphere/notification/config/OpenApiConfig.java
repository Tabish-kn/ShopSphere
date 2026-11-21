package com.shopsphere.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopSphere Notification Service API")
                        .version("1.0.0")
                        .description("Multi-channel Alerts, Email Dispatch, and WebSocket Real-Time Tracking Service")
                        .contact(new Contact().name("ShopSphere Architecture Team").email("architect@shopsphere.com")));
    }
}
