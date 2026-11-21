package com.shopsphere.cart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID productId;
    private String sku;
    private String productName;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal subtotal;
    private String imageUrl;

    public void updateQuantity(int newQuantity) {
        this.quantity = newQuantity;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(newQuantity));
    }
}
