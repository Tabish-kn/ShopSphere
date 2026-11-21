package com.shopsphere.notification.service;

import com.shopsphere.common.dto.PageResponse;
import com.shopsphere.common.event.NotificationEvent;
import com.shopsphere.common.exception.ResourceNotFoundException;
import com.shopsphere.notification.dto.NotificationResponse;
import com.shopsphere.notification.dto.WebSocketMessageDto;
import com.shopsphere.notification.entity.Notification;
import com.shopsphere.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    @Transactional
    public void processNotification(NotificationEvent event) {
        NotificationEvent.NotificationPayload payload = event.getPayload();
        UUID userId = payload.getUserId();
        String channel = payload.getChannel();

        log.info("Processing notification for user {}: '{}' (Channel: {})", userId, payload.getTitle(), channel);

        // 1. Persist notification to PostgreSQL
        Notification notification = Notification.builder()
                .userId(userId)
                .recipientEmail(payload.getRecipientEmail())
                .notificationType(payload.getNotificationType())
                .channel(channel)
                .title(payload.getTitle())
                .content(payload.getMessage())
                .referenceId(payload.getReferenceId())
                .read(false)
                .build();
        notificationRepository.save(notification);

        // 2. Dispatch WebSocket push if channel is WEBSOCKET or BOTH
        if ("WEBSOCKET".equalsIgnoreCase(channel) || "BOTH".equalsIgnoreCase(channel)) {
            dispatchWebSocketNotification(userId, payload);
        }

        // 3. Dispatch Email if channel is EMAIL or BOTH
        if ("EMAIL".equalsIgnoreCase(channel) || "BOTH".equalsIgnoreCase(channel)) {
            emailService.sendEmail(payload.getRecipientEmail(), payload.getTitle(), payload.getMessage());
        }
    }

    private void dispatchWebSocketNotification(UUID userId, NotificationEvent.NotificationPayload payload) {
        WebSocketMessageDto wsMessage = WebSocketMessageDto.builder()
                .type(payload.getNotificationType())
                .title(payload.getTitle())
                .message(payload.getMessage())
                .referenceId(payload.getReferenceId())
                .timestamp(Instant.now())
                .build();

        // 1. Send to Private User Queue: /user/{userId}/queue/notifications
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications",
                wsMessage
        );
        log.debug("Sent private WebSocket notification to user: {}", userId);

        // 2. If reference is an orderId, broadcast to public order tracker: /topic/orders/{orderId}
        if (payload.getReferenceId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/orders/" + payload.getReferenceId(),
                    wsMessage
            );
            log.debug("Broadcasted WebSocket update to: /topic/orders/{}", payload.getReferenceId());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getUserNotifications(UUID userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(
                page.getContent().stream().map(this::mapToResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (notification.getUserId().equals(userId)) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsReadForUser(userId);
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .notificationType(n.getNotificationType())
                .channel(n.getChannel())
                .title(n.getTitle())
                .content(n.getContent())
                .read(n.isRead())
                .referenceId(n.getReferenceId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
