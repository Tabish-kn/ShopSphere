package com.shopsphere.inventory.controller;

import com.shopsphere.common.dto.ApiResponse;
import com.shopsphere.common.security.SecurityConstants;
import com.shopsphere.inventory.dto.InventoryResponse;
import com.shopsphere.inventory.dto.StockUpdateRequest;
import com.shopsphere.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Product stock inquiries and admin inventory adjustments")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    @Operation(summary = "Check product stock availability (Public)")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(@PathVariable UUID productId) {
        InventoryResponse response = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Update available stock quantity (ADMIN only)")
    public ResponseEntity<ApiResponse<InventoryResponse>> updateStock(
            @RequestHeader(value = SecurityConstants.HEADER_USER_ROLES, required = false) String roles,
            @PathVariable UUID productId,
            @Valid @RequestBody StockUpdateRequest request) {

        if (roles == null || !roles.contains(SecurityConstants.ROLE_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only administrators can update inventory"));
        }

        InventoryResponse response = inventoryService.updateStock(productId, request.getAvailableQuantity());
        return ResponseEntity.ok(ApiResponse.success("Stock updated successfully", response));
    }
}
