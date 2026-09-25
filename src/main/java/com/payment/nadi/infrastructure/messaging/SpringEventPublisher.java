package com.payment.nadi.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.nadi.application.port.out.EventPublisher;
import com.payment.nadi.domain.event.PaymentDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Event publisher publishing domain events to Spring's event bus and logging formatted payloads.
 * Easily binds to Spring Cloud Stream / Kafka template when Kafka is enabled.
 */
@Component
public class SpringEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    public SpringEventPublisher(ApplicationEventPublisher applicationEventPublisher, ObjectMapper objectMapper) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(PaymentDomainEvent event) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            log.info("[EVENT_BUS] Publishing event: {} -> {}", event.getEventType(), jsonPayload);
        } catch (JsonProcessingException e) {
            log.info("[EVENT_BUS] Publishing event: {}", event.getEventType());
        }

        // Publish to in-process listeners (e.g. notifications, reporting, audits)
        applicationEventPublisher.publishEvent(event);
    }
}
