package com.shopsphere.inventory.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopSphere Inventory Service API")
                        .version("1.0.0")
                        .description("Stock Management, Concurrency Reservation, and Saga Compensation Service")
                        .contact(new Contact().name("ShopSphere Architecture Team").email("architect@shopsphere.com")));
    }
}
