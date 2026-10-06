package com.payment.nadi.application.service;

import com.payment.nadi.application.port.out.PushNotificationPort;
import com.payment.nadi.application.port.out.SmsAlertPort;
import com.payment.nadi.domain.event.PaymentCompletedEvent;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.event.TransferCompletedEvent;
import com.payment.nadi.domain.event.UserSecurityEvent;
import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.NotificationType;
import com.payment.nadi.domain.model.PaymentType;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationEventListenerTest {

    private SmsAlertPort smsAlertPort;
    private PushNotificationPort pushNotificationPort;
    private SpringDataUserAccountRepository userAccountRepository;
    private NotificationEventListener listener;

    private UserAccountEntity sender;
    private UserAccountEntity recipient;
    private UUID sourceAccId;
    private UUID destAccId;

    @BeforeEach
    void setUp() {
        smsAlertPort = mock(SmsAlertPort.class);
        pushNotificationPort = mock(PushNotificationPort.class);
        userAccountRepository = mock(SpringDataUserAccountRepository.class);

        listener = new NotificationEventListener(smsAlertPort, pushNotificationPort, userAccountRepository);

        sourceAccId = UUID.randomUUID();
        destAccId = UUID.randomUUID();

        sender = UserAccountEntity.builder()
                .id(UUID.randomUUID())
                .fullName("Sender User")
                .phoneNumber("+251911111111")
                .accountNumber("00111111")
                .sourceAccountId(sourceAccId)
                .smsNotificationsEnabled(true)
                .pushNotificationsEnabled(true)
                .pushToken("FCM_SENDER_TOKEN")
                .build();

        recipient = UserAccountEntity.builder()
                .id(UUID.randomUUID())
                .fullName("Recipient User")
                .phoneNumber("+251922222222")
                .accountNumber("00222222")
                .sourceAccountId(destAccId)
                .smsNotificationsEnabled(true)
                .pushNotificationsEnabled(true)
                .pushToken("FCM_RECIPIENT_TOKEN")
                .build();

        when(userAccountRepository.findBySourceAccountId(sourceAccId)).thenReturn(Optional.of(sender));
        when(userAccountRepository.findBySourceAccountId(destAccId)).thenReturn(Optional.of(recipient));
    }

    @Test
    @DisplayName("Should dispatch SMS and Push alerts to both sender and receiver on transfer completion")
    void shouldDispatchAlertsOnTransferCompleted() {
        TransferCompletedEvent event = TransferCompletedEvent.now(
                UUID.randomUUID(),
                "TXF-998877",
                Money.of(new BigDecimal("150.00"), "ETB"),
                sourceAccId,
                destAccId,
                "Gift transfer"
        );

        listener.onTransferCompleted(event);

        // Verify debit SMS & Push to sender
        verify(smsAlertPort).sendSms(eq(sender.getId()), eq("+251911111111"), contains("Debited 150.00 ETB"), eq(NotificationType.TRANSACTION_DEBIT), eq("TXF-998877"));
        verify(pushNotificationPort).sendPush(eq(sender.getId()), eq("FCM_SENDER_TOKEN"), eq("Transfer Sent"), anyString(), eq(NotificationType.TRANSACTION_DEBIT), eq("TXF-998877"), any());

        // Verify credit SMS & Push to recipient
        verify(smsAlertPort).sendSms(eq(recipient.getId()), eq("+251922222222"), contains("Received 150.00 ETB"), eq(NotificationType.TRANSACTION_CREDIT), eq("TXF-998877"));
        verify(pushNotificationPort).sendPush(eq(recipient.getId()), eq("FCM_RECIPIENT_TOKEN"), eq("Transfer Received"), anyString(), eq(NotificationType.TRANSACTION_CREDIT), eq("TXF-998877"), any());
    }

    @Test
    @DisplayName("Should dispatch top-up alert on PaymentCompletedEvent")
    void shouldDispatchPaymentCompletedAlert() {
        UUID customerId = UUID.randomUUID();
        when(userAccountRepository.findByCustomerId(customerId)).thenReturn(Optional.of(sender));

        PaymentCompletedEvent event = PaymentCompletedEvent.now(
                UUID.randomUUID(),
                "TOP-12345",
                PaymentType.TOP_UP,
                Money.of(new BigDecimal("500.00"), "ETB"),
                customerId,
                "PROVIDER-REF-1",
                "Telebirr"
        );

        listener.onPaymentCompleted(event);

        verify(smsAlertPort).sendSms(eq(sender.getId()), eq("+251911111111"), contains("500.00 ETB"), eq(NotificationType.TOP_UP_SUCCESS), eq("TOP-12345"));
        verify(pushNotificationPort).sendPush(eq(sender.getId()), eq("FCM_SENDER_TOKEN"), contains("Successful"), anyString(), eq(NotificationType.TOP_UP_SUCCESS), eq("TOP-12345"), any());
    }

    @Test
    @DisplayName("Should dispatch security alert on UserSecurityEvent")
    void shouldDispatchUserSecurityAlert() {
        UserSecurityEvent event = UserSecurityEvent.now(
                sender.getId(),
                sender.getPhoneNumber(),
                "2FA_ENABLED",
                "Two-Factor Authentication activated"
        );

        listener.onUserSecurityEvent(event);

        verify(smsAlertPort).sendSms(eq(sender.getId()), eq("+251911111111"), contains("2FA ENABLED"), eq(NotificationType.SECURITY_ALERT), anyString());
    }
}
