package com.shopsphere.order.repository;

import com.shopsphere.order.entity.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {

    @Query(value = "SELECT * FROM order_outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 50", nativeQuery = true)
    List<OrderOutbox> findPendingEvents();
}
