package com.shopsphere.cart.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cartOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopSphere Cart Service API")
                        .version("1.0.0")
                        .description("Redis-backed Shopping Cart Service with OpenFeign Product Validation")
                        .contact(new Contact().name("ShopSphere Architecture Team").email("architect@shopsphere.com")));
    }
}
