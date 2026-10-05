package com.payment.nadi.infrastructure.messaging;

import com.payment.nadi.domain.event.PaymentDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens to domain events published to the Spring event bus
 * and forwards them to Apache Kafka.
 */
@Component
public class KafkaPaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentEventListener.class);

    private final KafkaPaymentProducer kafkaPaymentProducer;

    public KafkaPaymentEventListener(KafkaPaymentProducer kafkaPaymentProducer) {
        this.kafkaPaymentProducer = kafkaPaymentProducer;
    }

    @EventListener
    public void onPaymentDomainEvent(PaymentDomainEvent event) {
        log.debug("[KAFKA_LISTENER] Intercepted domain event {} [id={}], forwarding to Kafka",
                event.getEventType(), event.getEventId());
        kafkaPaymentProducer.publish(event);
    }
}
