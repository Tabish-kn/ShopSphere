package com.shopsphere.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.common.dto.PageResponse;
import com.shopsphere.common.event.OrderCreatedEvent;
import com.shopsphere.common.exception.BadRequestException;
import com.shopsphere.common.exception.ResourceNotFoundException;
import com.shopsphere.order.client.CartFeignClient;
import com.shopsphere.order.dto.CreateOrderRequest;
import com.shopsphere.order.dto.OrderItemResponse;
import com.shopsphere.order.dto.OrderResponse;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderItem;
import com.shopsphere.order.entity.OrderOutbox;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.repository.OrderOutboxRepository;
import com.shopsphere.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final CartFeignClient cartFeignClient;
    private final ObjectMapper objectMapper;

    /**
     * Saga Step 1: Creates an Order and writes to Outbox atomically in PostgreSQL.
     */
    @Transactional
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request, String correlationId) {
        String orderNumber = generateOrderNumber();

        BigDecimal totalAmount = BigDecimal.ZERO;

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .userId(userId)
                .status(OrderStatus.CREATED)
                .shippingAddressJson(request.getShippingAddress())
                .paymentMethod(request.getPaymentMethod())
                .build();

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            BigDecimal subtotal = itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = OrderItem.builder()
                    .productId(itemReq.getProductId())
                    .productName(itemReq.getProductName())
                    .sku(itemReq.getSku())
                    .unitPrice(itemReq.getUnitPrice())
                    .quantity(itemReq.getQuantity())
                    .subtotal(subtotal)
                    .build();

            order.addItem(orderItem);
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);

        // Build domain event payload
        List<OrderCreatedEvent.OrderItemPayload> eventItems = savedOrder.getItems().stream()
                .map(i -> OrderCreatedEvent.OrderItemPayload.builder()
                        .productId(i.getProductId())
                        .sku(i.getSku())
                        .productName(i.getProductName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .build())
                .toList();

        OrderCreatedEvent.OrderPayload payload = OrderCreatedEvent.OrderPayload.builder()
                .orderId(savedOrder.getId())
                .orderNumber(savedOrder.getOrderNumber())
                .userId(userId)
                .totalAmount(savedOrder.getTotalAmount())
                .currency(savedOrder.getCurrency())
                .paymentMethod(savedOrder.getPaymentMethod())
                .items(eventItems)
                .build();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .payload(payload)
                .build();
        event.initializeMetadata("ORDER_CREATED", "ORDER", savedOrder.getId().toString(), correlationId);

        // Transactional Outbox write in the exact same DB transaction!
        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            OrderOutbox outbox = OrderOutbox.builder()
                    .aggregateType("ORDER")
                    .aggregateId(savedOrder.getId().toString())
                    .eventType("ORDER_CREATED")
                    .payloadJson(payloadJson)
                    .status("PENDING")
                    .build();
            outboxRepository.save(outbox);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize order event for outbox", e);
        }

        // Asynchronously clear cart via Feign
        try {
            cartFeignClient.clearCart(userId);
        } catch (Exception e) {
            log.warn("Failed to clear cart for user {}: {}", userId, e.getMessage());
        }

        log.info("Created order {} (ID: {}) for user {}", orderNumber, savedOrder.getId(), userId);
        return mapToResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getUserOrders(UUID userId, Pageable pageable) {
        Page<Order> page = orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(
                page.getContent().stream().map(this::mapToResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID userId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUserId().equals(userId)) {
            throw new BadRequestException("You do not have permission to cancel this order");
        }

        if (order.getStatus() != OrderStatus.CREATED &&
                order.getStatus() != OrderStatus.INVENTORY_RESERVED &&
                order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BadRequestException("Order cannot be cancelled in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason != null ? reason : "Cancelled by customer");
        Order updated = orderRepository.save(order);

        log.info("Order {} cancelled by user {}. Reason: {}", orderId, userId, reason);
        return mapToResponse(updated);
    }

    @Transactional
    public void updateOrderStatus(UUID orderId, OrderStatus newStatus, String reason) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(newStatus);
            if (reason != null) {
                order.setCancellationReason(reason);
            }
            orderRepository.save(order);
            log.info("Transitioned order {} status to {}", orderId, newStatus);
        });
    }

    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "ORD-" + datePart + "-" + randomPart;
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> OrderItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .sku(i.getSku())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .subtotal(i.getSubtotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .shippingAddress(order.getShippingAddressJson())
                .paymentMethod(order.getPaymentMethod())
                .cancellationReason(order.getCancellationReason())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
