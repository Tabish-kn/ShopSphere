package com.shopsphere.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopSphere Order Service API")
                        .version("1.0.0")
                        .description("Order Lifecycle, Transactional Outbox, and Saga Initiator Service")
                        .contact(new Contact().name("ShopSphere Architecture Team").email("architect@shopsphere.com")));
    }
}
