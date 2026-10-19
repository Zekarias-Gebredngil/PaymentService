package com.payment.nadi.infrastructure.messaging;

import com.payment.nadi.domain.event.PaymentDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KafkaPaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentEventListener.class);

    private final KafkaPaymentProducer kafkaPaymentProducer;

    public KafkaPaymentEventListener(KafkaPaymentProducer kafkaPaymentProducer) {
        this.kafkaPaymentProducer = kafkaPaymentProducer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentDomainEvent(PaymentDomainEvent event) {
        log.debug("[KAFKA_LISTENER] Intercepted domain event {} [id={}] after commit, forwarding to Kafka",
                event.getEventType(), event.getEventId());
        kafkaPaymentProducer.publish(event);
    }
}
