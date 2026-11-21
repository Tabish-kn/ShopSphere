package com.shopsphere.product.repository;

import com.shopsphere.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySlug(String slug);
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
    boolean existsBySlug(String slug);

    @Query(value = """
            SELECT p FROM Product p
            JOIN FETCH p.category c
            WHERE (:active IS NULL OR p.active = :active)
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:query IS NULL OR (
                    LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
                    LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')) OR
                    LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
              ))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE (:active IS NULL OR p.active = :active)
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:query IS NULL OR (
                    LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR
                    LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')) OR
                    LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
              ))
            """)
    Page<Product> searchProducts(
            @Param("query") String query,
            @Param("categoryId") Long categoryId,
            @Param("active") Boolean active,
            Pageable pageable
    );
}
