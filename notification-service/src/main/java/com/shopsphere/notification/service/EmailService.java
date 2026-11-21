package com.shopsphere.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Async
    public void sendEmail(String recipientEmail, String subject, String body) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("Skipping email dispatch: Recipient email is missing.");
            return;
        }

        // Simulating SMTP transmission
        log.info("[MOCK EMAIL DISPATCH] To: {} | Subject: '{}' | Content Preview: '{}'",
                recipientEmail, subject, body.length() > 60 ? body.substring(0, 60) + "..." : body);
    }
}
