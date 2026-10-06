package com.payment.nadi.infrastructure.messaging;

import com.payment.nadi.domain.event.PaymentCompletedEvent;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.event.PaymentInitiatedEvent;
import com.payment.nadi.domain.event.TransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumer for listening to payment domain events published to Kafka.
 * Can be enabled/disabled via configuration property 'kafka.consumer.enabled'.
 */
@Component
@ConditionalOnProperty(prefix = "kafka.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaPaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentEventConsumer.class);

    @KafkaListener(
            topics = "${kafka.topics.payment-completed:payment.completed}",
            groupId = "${spring.kafka.consumer.group-id:payment-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCompleted(
            @Payload Object payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[KAFKA_CONSUMER] Payment SUCCESS event received on topic {} [partition={}, offset={}]: {}",
                topic, partition, offset, payload);

        if (payload instanceof PaymentCompletedEvent completedEvent) {
            log.info("[KAFKA_CONSUMER] Successfully processed payment completed for paymentId={}, reference={}, amount={}",
                    completedEvent.paymentId(), completedEvent.reference(), completedEvent.amount());
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.payment-failed:payment.failed}",
            groupId = "${spring.kafka.consumer.group-id:payment-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentFailed(
            @Payload Object payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.warn("[KAFKA_CONSUMER] Payment FAILED event received on topic {} [partition={}, offset={}]: {}",
                topic, partition, offset, payload);

        if (payload instanceof PaymentFailedEvent failedEvent) {
            log.warn("[KAFKA_CONSUMER] Alert: Payment failed for paymentId={}, reference={}, reason={}",
                    failedEvent.paymentId(), failedEvent.reference(), failedEvent.reason());
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.payment-initiated:payment.initiated}",
            groupId = "${spring.kafka.consumer.group-id:payment-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentInitiated(
            @Payload Object payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[KAFKA_CONSUMER] Payment INITIATED event received on topic {} [partition={}, offset={}]: {}",
                topic, partition, offset, payload);

        if (payload instanceof PaymentInitiatedEvent initiatedEvent) {
            log.info("[KAFKA_CONSUMER] Tracking initiated payment for paymentId={}, provider={}",
                    initiatedEvent.paymentId(), initiatedEvent.provider());
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.transfer-completed:payment.transfer-completed}",
            groupId = "${spring.kafka.consumer.group-id:payment-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTransferCompleted(
            @Payload Object payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[KAFKA_CONSUMER] Transfer COMPLETED event received on topic {} [partition={}, offset={}]: {}",
                topic, partition, offset, payload);

        if (payload instanceof TransferCompletedEvent transferEvent) {
            log.info("[KAFKA_CONSUMER] Processed transfer paymentId={}, sourceAccount={}, destinationAccount={}, amount={}",
                    transferEvent.paymentId(), transferEvent.sourceAccountId(),
                    transferEvent.destinationAccountId(), transferEvent.amount());
        }
    }
}
