package com.payment.nadi.infrastructure.messaging;

import com.payment.nadi.domain.event.PaymentCompletedEvent;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.*;

class KafkaPaymentEventListenerTest {

    private KafkaPaymentProducer kafkaPaymentProducer;
    private KafkaPaymentEventListener eventListener;

    @BeforeEach
    void setUp() {
        kafkaPaymentProducer = mock(KafkaPaymentProducer.class);
        eventListener = new KafkaPaymentEventListener(kafkaPaymentProducer);
    }

    @Test
    @DisplayName("Should forward payment success domain event to Kafka producer")
    void shouldForwardPaymentCompletedEvent() {
        PaymentCompletedEvent event = PaymentCompletedEvent.now(
                UUID.randomUUID(),
                "REF-COMPLETED-TEST",
                PaymentType.TOP_UP,
                Money.of(new BigDecimal("100.00"), "ETB"),
                UUID.randomUUID(),
                "PROV-111",
                "TELEBIRR"
        );

        eventListener.onPaymentDomainEvent(event);

        verify(kafkaPaymentProducer, times(1)).publish(event);
    }

    @Test
    @DisplayName("Should forward payment failed domain event to Kafka producer")
    void shouldForwardPaymentFailedEvent() {
        PaymentFailedEvent event = PaymentFailedEvent.now(
                UUID.randomUUID(),
                "REF-FAILED-TEST",
                PaymentType.WITHDRAW,
                Money.of(new BigDecimal("200.00"), "ETB"),
                UUID.randomUUID(),
                "Exceeded daily transaction limit"
        );

        eventListener.onPaymentDomainEvent(event);

        verify(kafkaPaymentProducer, times(1)).publish(event);
    }
}
