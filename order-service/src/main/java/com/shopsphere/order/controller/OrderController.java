package com.shopsphere.order.controller;

import com.shopsphere.common.dto.ApiResponse;
import com.shopsphere.common.dto.PageResponse;
import com.shopsphere.common.security.SecurityConstants;
import com.shopsphere.order.dto.CreateOrderRequest;
import com.shopsphere.order.dto.OrderResponse;
import com.shopsphere.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order Placement, History, and Lifecycle Management APIs")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place a new order (Initiates Distributed Saga via Transactional Outbox)")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false) String correlationId,
            @Valid @RequestBody CreateOrderRequest request) {

        OrderResponse response = orderService.createOrder(userId, request, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", response));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order details by order ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable UUID orderId) {
        OrderResponse response = orderService.getOrderById(orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Get authenticated user's order history")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getUserOrders(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<OrderResponse> response = orderService.getUserOrders(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId,
            @PathVariable UUID orderId,
            @RequestParam(required = false, defaultValue = "Customer requested cancellation") String reason) {

        OrderResponse response = orderService.cancelOrder(orderId, userId, reason);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", response));
    }
}
