package com.payment.nadi.infrastructure.messaging;

import com.payment.nadi.domain.event.PaymentCompletedEvent;
import com.payment.nadi.domain.event.PaymentDomainEvent;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.event.PaymentInitiatedEvent;
import com.payment.nadi.domain.event.TransferCompletedEvent;
import com.payment.nadi.infrastructure.configuration.KafkaTopicProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Producer for publishing domain payment events to Apache Kafka.
 * Routes events to dedicated topics as well as the aggregate events topic.
 */
@Component
public class KafkaPaymentProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;

    public KafkaPaymentProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                KafkaTopicProperties topicProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicProperties = topicProperties;
    }

    /**
     * Publishes any PaymentDomainEvent to its designated Kafka topic and the aggregate stream.
     */
    public CompletableFuture<SendResult<String, Object>> publish(PaymentDomainEvent event) {
        String targetTopic = resolveTopicForEvent(event);
        String key = event.getPaymentId() != null ? event.getPaymentId().toString() : event.getEventId().toString();

        // 1. Send to dedicated topic for this event type
        CompletableFuture<SendResult<String, Object>> future = sendRecord(targetTopic, key, event);

        // 2. Also forward to the aggregate all-events stream
        if (!targetTopic.equals(topicProperties.getAllEvents())) {
            sendRecord(topicProperties.getAllEvents(), key, event);
        }

        return future;
    }

    public CompletableFuture<SendResult<String, Object>> sendPaymentCompleted(PaymentCompletedEvent event) {
        return publish(event);
    }

    public CompletableFuture<SendResult<String, Object>> sendPaymentFailed(PaymentFailedEvent event) {
        return publish(event);
    }

    public CompletableFuture<SendResult<String, Object>> sendPaymentInitiated(PaymentInitiatedEvent event) {
        return publish(event);
    }

    public CompletableFuture<SendResult<String, Object>> sendTransferCompleted(TransferCompletedEvent event) {
        return publish(event);
    }

    private CompletableFuture<SendResult<String, Object>> sendRecord(String topic, String key, PaymentDomainEvent event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        record.headers()
                .add(new RecordHeader("event_id", event.getEventId().toString().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader("event_type", event.getEventType().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader("occurred_at", event.getOccurredAt().toString().getBytes(StandardCharsets.UTF_8)));

        if (event.getPaymentId() != null) {
            record.headers().add(new RecordHeader("payment_id", event.getPaymentId().toString().getBytes(StandardCharsets.UTF_8)));
        }

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(record);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KAFKA_PRODUCER] Failed to publish event {} [id={}] to topic {}: {}",
                        event.getEventType(), event.getEventId(), topic, ex.getMessage(), ex);
            } else {
                log.info("[KAFKA_PRODUCER] Event {} [id={}] published to topic {} [partition={}, offset={}]",
                        event.getEventType(),
                        event.getEventId(),
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }

    private String resolveTopicForEvent(PaymentDomainEvent event) {
        return switch (event.getEventType()) {
            case "PAYMENT_COMPLETED" -> topicProperties.getPaymentCompleted();
            case "PAYMENT_FAILED" -> topicProperties.getPaymentFailed();
            case "PAYMENT_INITIATED" -> topicProperties.getPaymentInitiated();
            case "TRANSFER_COMPLETED" -> topicProperties.getTransferCompleted();
            default -> topicProperties.getAllEvents();
        };
    }
}
