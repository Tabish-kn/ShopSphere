package com.shopsphere.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopSphere Payment Service API")
                        .version("1.0.0")
                        .description("Idempotent Payment Processing, Mock Gateway, and Saga Event Coordination Service")
                        .contact(new Contact().name("ShopSphere Architecture Team").email("architect@shopsphere.com")));
    }
}
