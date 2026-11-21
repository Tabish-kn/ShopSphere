package com.shopsphere.cart.client;

import com.shopsphere.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * OpenFeign client for synchronous communication with PRODUCT-SERVICE.
 * Uses Eureka service discovery to load-balance requests across available instances.
 */
@FeignClient(name = "product-service", fallback = ProductClientFallback.class)
public interface ProductFeignClient {

    @GetMapping("/api/v1/products/{id}")
    ApiResponse<ProductSnapshotDto> getProductById(@PathVariable("id") UUID id);

    record ProductSnapshotDto(
            UUID id,
            String name,
            String sku,
            BigDecimal price,
            boolean active,
            String imageUrl
    ) {}
}
