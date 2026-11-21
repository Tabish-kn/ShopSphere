package com.shopsphere.common.tracing;

import com.shopsphere.common.security.SecurityConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * OpenFeign RequestInterceptor that propagates X-Correlation-Id across synchronous service calls.
 */
@Component
public class FeignCorrelationInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = null;

        // 1. Try to extract correlation ID from incoming HTTP servlet request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            correlationId = request.getHeader(SecurityConstants.HEADER_CORRELATION_ID);
        }

        // 2. Fallback to MDC (Mapped Diagnostic Context)
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get(SecurityConstants.HEADER_CORRELATION_ID);
        }

        // 3. Fallback to generating a fresh ID
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        template.header(SecurityConstants.HEADER_CORRELATION_ID, correlationId);
    }
}
