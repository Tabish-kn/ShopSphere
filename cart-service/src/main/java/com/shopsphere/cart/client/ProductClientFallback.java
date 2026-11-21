package com.shopsphere.cart.client;

import com.shopsphere.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resilience4j fallback when Product Service is unreachable or open-circuited.
 */
@Component
public class ProductClientFallback implements ProductFeignClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClientFallback.class);

    @Override
    public ApiResponse<ProductSnapshotDto> getProductById(UUID id) {
        log.error("Circuit Breaker Fallback: Failed to fetch product info from Product Service for ID: {}", id);
        return ApiResponse.error("Product service is temporarily unavailable. Please try again later.");
    }
}
