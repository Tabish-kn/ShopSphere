package com.shopsphere.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal price;
    private String sku;
    private Long categoryId;
    private String categoryName;
    private String imageUrl;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
