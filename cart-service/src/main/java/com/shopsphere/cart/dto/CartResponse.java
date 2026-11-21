package com.shopsphere.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;
    private List<CartItemResponse> items;
    private BigDecimal totalAmount;
    private int totalItemsCount;
    private Instant updatedAt;
}
