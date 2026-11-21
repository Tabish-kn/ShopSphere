package com.shopsphere.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Base Enterprise CloudEvent-style envelope for all Kafka messages.
 * Contains metadata for distributed tracing, versioning, and idempotency.
 *
 * @param <T> Domain event payload type
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this specific event message (used for idempotency checks).
     */
    private String eventId;

    /**
     * Name of the domain event (e.g. ORDER_CREATED, PAYMENT_SUCCESS).
     */
    private String eventType;

    /**
     * Timestamp when the event was produced.
     */
    private Instant eventTimestamp;

    /**
     * Domain aggregate type (e.g. ORDER, INVENTORY, PAYMENT).
     */
    private String aggregateType;

    /**
     * Primary key of the aggregate (e.g. orderId).
     */
    private String aggregateId;

    /**
     * Distributed correlation ID for end-to-end tracing across services.
     */
    private String correlationId;

    /**
     * Schema version for forward/backward compatibility.
     */
    private int version;

    /**
     * Domain payload data.
     */
    private T payload;

    public void initializeMetadata(String eventType, String aggregateType, String aggregateId, String correlationId) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.eventTimestamp = Instant.now();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.correlationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        this.version = 1;
    }
}
