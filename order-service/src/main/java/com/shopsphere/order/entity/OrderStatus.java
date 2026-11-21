package com.shopsphere.order.entity;

/**
 * State machine representing the order fulfillment lifecycle.
 */
public enum OrderStatus {
    CREATED,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    FAILED
}
