package com.shopsphere.order.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.common.event.OrderCreatedEvent;
import com.shopsphere.order.entity.OrderOutbox;
import com.shopsphere.order.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Background outbox publisher.
 * Polls pending domain events from PostgreSQL and publishes them to Kafka with At-Least-Once guarantees.
 */
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OrderOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000) // Poll every 1 second
    @Transactional
    public void publishPendingEvents() {
        List<OrderOutbox> pendingEvents = outboxRepository.findPendingEvents();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to publish to Kafka", pendingEvents.size());

        for (OrderOutbox outbox : pendingEvents) {
            try {
                if ("ORDER_CREATED".equals(outbox.getEventType())) {
                    OrderCreatedEvent event = objectMapper.readValue(outbox.getPayloadJson(), OrderCreatedEvent.class);
                    kafkaTemplate.send("order-created", outbox.getAggregateId(), event).get(); // Synchronous get to ensure ack
                }

                outbox.setStatus("PUBLISHED");
                outbox.setPublishedAt(Instant.now());
                outboxRepository.save(outbox);
                log.info("Published outbox event {} for aggregate {}", outbox.getId(), outbox.getAggregateId());

            } catch (Exception e) {
                outbox.setRetryCount(outbox.getRetryCount() + 1);
                if (outbox.getRetryCount() >= 5) {
                    outbox.setStatus("FAILED");
                    log.error("Outbox event {} exceeded max retries. Marked as FAILED.", outbox.getId(), e);
                } else {
                    log.warn("Failed to publish outbox event {}. Will retry. Reason: {}", outbox.getId(), e.getMessage());
                }
                outboxRepository.save(outbox);
            }
        }
    }
}
