package com.payment.nadi.application.port.out;

import com.payment.nadi.domain.event.PaymentDomainEvent;

/**
 * Port for asynchronous event publishing (Kafka, RabbitMQ, Spring Event Bus).
 */
public interface EventPublisher {

    void publish(PaymentDomainEvent event);
}
