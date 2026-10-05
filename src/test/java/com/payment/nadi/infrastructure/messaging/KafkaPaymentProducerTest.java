package com.payment.nadi.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.nadi.domain.event.PaymentCompletedEvent;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.event.PaymentInitiatedEvent;
import com.payment.nadi.domain.event.TransferCompletedEvent;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.infrastructure.configuration.JacksonConfig;
import com.payment.nadi.infrastructure.configuration.KafkaTopicProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KafkaPaymentProducerTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaTopicProperties topicProperties;
    private KafkaPaymentProducer producer;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        topicProperties = new KafkaTopicProperties();
        producer = new KafkaPaymentProducer(kafkaTemplate, topicProperties);
        objectMapper = new JacksonConfig().objectMapper();

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Test
    @DisplayName("Should publish PaymentCompletedEvent to payment.completed and aggregate stream with paymentId key")
    void shouldPublishPaymentCompletedEvent() {
        UUID paymentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        PaymentCompletedEvent event = PaymentCompletedEvent.now(
                paymentId,
                "REF-COMPLETED-101",
                PaymentType.TOP_UP,
                Money.of(new BigDecimal("250.00"), "ETB"),
                customerId,
                "PROV-REF-123",
                "TELEBIRR"
        );

        producer.sendPaymentCompleted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());

        List<ProducerRecord<String, Object>> sentRecords = captor.getAllValues();
        assertThat(sentRecords).hasSize(2);

        // First record -> dedicated topic
        ProducerRecord<String, Object> dedicatedRecord = sentRecords.get(0);
        assertThat(dedicatedRecord.topic()).isEqualTo("payment.completed");
        assertThat(dedicatedRecord.key()).isEqualTo(paymentId.toString());
        assertThat(dedicatedRecord.value()).isEqualTo(event);
        assertThat(dedicatedRecord.headers().lastHeader("event_type").value())
                .isEqualTo("PAYMENT_COMPLETED".getBytes());

        // Second record -> aggregate stream
        ProducerRecord<String, Object> aggregateRecord = sentRecords.get(1);
        assertThat(aggregateRecord.topic()).isEqualTo("payment.events");
        assertThat(aggregateRecord.key()).isEqualTo(paymentId.toString());
    }

    @Test
    @DisplayName("Should publish PaymentFailedEvent to payment.failed topic with reason and amount")
    void shouldPublishPaymentFailedEvent() {
        UUID paymentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        PaymentFailedEvent event = PaymentFailedEvent.now(
                paymentId,
                "REF-FAILED-202",
                PaymentType.WITHDRAW,
                Money.of(new BigDecimal("500.00"), "ETB"),
                customerId,
                "Insufficient funds in wallet"
        );

        producer.sendPaymentFailed(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());

        ProducerRecord<String, Object> failedRecord = captor.getAllValues().get(0);
        assertThat(failedRecord.topic()).isEqualTo("payment.failed");
        assertThat(failedRecord.key()).isEqualTo(paymentId.toString());
        assertThat(failedRecord.value()).isEqualTo(event);
        assertThat(failedRecord.headers().lastHeader("event_type").value())
                .isEqualTo("PAYMENT_FAILED".getBytes());
    }

    @Test
    @DisplayName("Should publish PaymentInitiatedEvent to payment.initiated topic")
    void shouldPublishPaymentInitiatedEvent() {
        UUID paymentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        PaymentInitiatedEvent event = PaymentInitiatedEvent.now(
                paymentId,
                "REF-INIT-303",
                PaymentType.TOP_UP,
                Money.of(new BigDecimal("100.00"), "ETB"),
                customerId,
                "TELEBIRR"
        );

        producer.sendPaymentInitiated(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());

        ProducerRecord<String, Object> initiatedRecord = captor.getAllValues().get(0);
        assertThat(initiatedRecord.topic()).isEqualTo("payment.initiated");
        assertThat(initiatedRecord.key()).isEqualTo(paymentId.toString());
    }

    @Test
    @DisplayName("Should publish TransferCompletedEvent to payment.transfer-completed topic")
    void shouldPublishTransferCompletedEvent() {
        UUID paymentId = UUID.randomUUID();
        UUID sourceAccount = UUID.randomUUID();
        UUID destAccount = UUID.randomUUID();
        TransferCompletedEvent event = TransferCompletedEvent.now(
                paymentId,
                "REF-TRF-404",
                Money.of(new BigDecimal("1200.00"), "ETB"),
                sourceAccount,
                destAccount,
                "Monthly peer-to-peer transfer"
        );

        producer.sendTransferCompleted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());

        ProducerRecord<String, Object> transferRecord = captor.getAllValues().get(0);
        assertThat(transferRecord.topic()).isEqualTo("payment.transfer-completed");
        assertThat(transferRecord.key()).isEqualTo(paymentId.toString());
    }

    @Test
    @DisplayName("Should serialize and deserialize PaymentCompletedEvent to/from JSON cleanly")
    void shouldSerializePaymentCompletedEventToJson() throws Exception {
        UUID paymentId = UUID.randomUUID();
        PaymentCompletedEvent event = PaymentCompletedEvent.now(
                paymentId,
                "REF-JSON-TEST",
                PaymentType.TOP_UP,
                Money.of(new BigDecimal("75.50"), "ETB"),
                UUID.randomUUID(),
                "PROV-999",
                "TELEBIRR"
        );

        String json = objectMapper.writeValueAsString(event);
        assertThat(json).contains("PAYMENT_COMPLETED");
        assertThat(json).contains("75.50");
        assertThat(json).contains("ETB");

        PaymentCompletedEvent deserialized = objectMapper.readValue(json, PaymentCompletedEvent.class);
        assertThat(deserialized.paymentId()).isEqualTo(paymentId);
        assertThat(deserialized.amount().amount()).isEqualByComparingTo(new BigDecimal("75.50"));
        assertThat(deserialized.amount().getCurrencyCode()).isEqualTo("ETB");
    }

    @Test
    @DisplayName("Should serialize and deserialize PaymentFailedEvent to/from JSON cleanly")
    void shouldSerializePaymentFailedEventToJson() throws Exception {
        UUID paymentId = UUID.randomUUID();
        PaymentFailedEvent event = PaymentFailedEvent.now(
                paymentId,
                "REF-JSON-FAIL",
                PaymentType.WITHDRAW,
                Money.of(new BigDecimal("30.00"), "ETB"),
                UUID.randomUUID(),
                "Invalid PIN code"
        );

        String json = objectMapper.writeValueAsString(event);
        assertThat(json).contains("PAYMENT_FAILED");
        assertThat(json).contains("Invalid PIN code");

        PaymentFailedEvent deserialized = objectMapper.readValue(json, PaymentFailedEvent.class);
        assertThat(deserialized.paymentId()).isEqualTo(paymentId);
        assertThat(deserialized.reason()).isEqualTo("Invalid PIN code");
    }
}
