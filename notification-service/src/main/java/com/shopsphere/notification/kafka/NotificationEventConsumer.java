package com.shopsphere.notification.kafka;

import com.shopsphere.common.event.NotificationEvent;
import com.shopsphere.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
    private final NotificationService notificationService;

    @KafkaListener(topics = "notification-events", groupId = "notification-group")
    public void consumeNotificationEvent(NotificationEvent event) {
        log.info("Received NotificationEvent for user: {}. Title: '{}'",
                event.getPayload().getUserId(), event.getPayload().getTitle());
        notificationService.processNotification(event);
    }
}
