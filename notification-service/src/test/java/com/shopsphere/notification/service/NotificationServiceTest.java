package com.shopsphere.notification.service;

import com.shopsphere.common.event.NotificationEvent;
import com.shopsphere.notification.dto.WebSocketMessageDto;
import com.shopsphere.notification.entity.Notification;
import com.shopsphere.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should successfully persist notification, push via WebSocket, and send email for BOTH channel")
    void shouldProcessMultiChannelNotification() {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId("corr-notif-1")
                .payload(NotificationEvent.NotificationPayload.builder()
                        .userId(userId)
                        .recipientEmail("user@example.com")
                        .title("Order Confirmed")
                        .message("Your order has been paid and confirmed.")
                        .notificationType("ORDER_STATUS")
                        .channel("BOTH")
                        .referenceId(orderId)
                        .build())
                .build();

        notificationService.processNotification(event);

        // Verify DB persistence
        verify(notificationRepository, times(1)).save(any(Notification.class));

        // Verify WebSocket Private & Topic broadcast
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq(userId.toString()),
                eq("/queue/notifications"),
                any(WebSocketMessageDto.class)
        );
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/orders/" + orderId),
                any(WebSocketMessageDto.class)
        );

        // Verify Email dispatched asynchronously
        verify(emailService, times(1)).sendEmail(
                eq("user@example.com"),
                eq("Order Confirmed"),
                eq("Your order has been paid and confirmed.")
        );
    }
}
