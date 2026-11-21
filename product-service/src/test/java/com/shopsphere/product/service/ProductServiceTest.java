package com.shopsphere.product.service;

import com.shopsphere.common.exception.ConflictException;
import com.shopsphere.common.exception.ResourceNotFoundException;
import com.shopsphere.product.dto.ProductCreateRequest;
import com.shopsphere.product.dto.ProductResponse;
import com.shopsphere.product.entity.Category;
import com.shopsphere.product.entity.Product;
import com.shopsphere.product.repository.CategoryRepository;
import com.shopsphere.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Category category;
    private Product product;
    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();

        category = Category.builder()
                .id(1L)
                .name("Electronics")
                .slug("electronics")
                .active(true)
                .build();

        product = Product.builder()
                .id(productId)
                .name("Wireless Noise Canceling Headphones")
                .slug("wireless-noise-canceling-headphones-123")
                .description("Top-tier sound quality")
                .price(new BigDecimal("299.99"))
                .sku("HEADPHONE-NC-001")
                .category(category)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should successfully create a product")
    void shouldCreateProduct() {
        ProductCreateRequest request = ProductCreateRequest.builder()
                .name("Wireless Noise Canceling Headphones")
                .description("Top-tier sound quality")
                .price(new BigDecimal("299.99"))
                .sku("HEADPHONE-NC-001")
                .categoryId(1L)
                .active(true)
                .build();

        when(productRepository.existsBySku("HEADPHONE-NC-001")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals(productId, response.getId());
        assertEquals("HEADPHONE-NC-001", response.getSku());
        assertEquals("Electronics", response.getCategoryName());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when SKU already exists")
    void shouldThrowConflictWhenSkuExists() {
        ProductCreateRequest request = ProductCreateRequest.builder()
                .name("Sample Product")
                .price(new BigDecimal("19.99"))
                .sku("HEADPHONE-NC-001")
                .categoryId(1L)
                .build();

        when(productRepository.existsBySku("HEADPHONE-NC-001")).thenReturn(true);

        assertThrows(ConflictException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when fetching non-existent product")
    void shouldThrowNotFoundForInvalidId() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(unknownId));
    }
}
