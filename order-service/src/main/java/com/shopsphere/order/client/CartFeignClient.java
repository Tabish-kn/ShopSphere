package com.shopsphere.order.client;

import com.shopsphere.common.dto.ApiResponse;
import com.shopsphere.common.security.SecurityConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "cart-service")
public interface CartFeignClient {

    @DeleteMapping("/api/v1/cart")
    ApiResponse<Void> clearCart(@RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId);
}
