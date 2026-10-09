package com.payment.nadi.application.service;

import com.payment.nadi.application.port.out.PushNotificationPort;
import com.payment.nadi.application.port.out.SmsAlertPort;
import com.payment.nadi.domain.event.PaymentCompletedEvent;
import com.payment.nadi.domain.event.PaymentFailedEvent;
import com.payment.nadi.domain.event.TransferCompletedEvent;
import com.payment.nadi.domain.event.UserSecurityEvent;
import com.payment.nadi.domain.model.NotificationType;
import com.payment.nadi.infrastructure.persistence.entity.UserAccountEntity;
import com.payment.nadi.infrastructure.persistence.repository.SpringDataUserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Optional;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final SmsAlertPort smsAlertPort;
    private final PushNotificationPort pushNotificationPort;
    private final SpringDataUserAccountRepository userAccountRepository;

    public NotificationEventListener(
            SmsAlertPort smsAlertPort,
            PushNotificationPort pushNotificationPort,
            SpringDataUserAccountRepository userAccountRepository) {
        this.smsAlertPort = smsAlertPort;
        this.pushNotificationPort = pushNotificationPort;
        this.userAccountRepository = userAccountRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTransferCompleted(TransferCompletedEvent event) {
        log.info("[NOTIFICATION_LISTENER] Processing transfer alerts for ref: {}", event.reference());

        // 1. Alert Sender (Debit)
        Optional<UserAccountEntity> senderOpt = userAccountRepository.findBySourceAccountId(event.sourceAccountId())
                .or(() -> userAccountRepository.findById(event.sourceAccountId()));

        // 2. Alert Recipient (Credit)
        Optional<UserAccountEntity> recipientOpt = userAccountRepository
                .findBySourceAccountId(event.destinationAccountId())
                .or(() -> userAccountRepository.findById(event.destinationAccountId()));

        String recipientInfo = recipientOpt
                .map(u -> u.getFullName() + " (" + maskAccountNumber(u.getAccountNumber()) + ")")
                .orElse("account " + event.destinationAccountId());
        String senderInfo = senderOpt.map(u -> u.getFullName() + " (" + maskAccountNumber(u.getAccountNumber()) + ")")
                .orElse("account " + event.sourceAccountId());

        if (senderOpt.isPresent()) {
            UserAccountEntity sender = senderOpt.get();
            String debitMsg = String.format("Nadi Pay Alert: Debited %s %s to %s. Ref: %s.",
                    event.amount().amount(), event.amount().currency(), recipientInfo, event.reference());

            if (Boolean.TRUE.equals(sender.getSmsNotificationsEnabled())) {
                smsAlertPort.sendSms(sender.getId(), sender.getPhoneNumber(), debitMsg,
                        NotificationType.TRANSACTION_DEBIT, event.reference());
            }
            if (Boolean.TRUE.equals(sender.getPushNotificationsEnabled()) && sender.getPushToken() != null) {
                pushNotificationPort.sendPush(sender.getId(), sender.getPushToken(), "Transfer Sent", debitMsg,
                        NotificationType.TRANSACTION_DEBIT, event.reference(),
                        Map.of("reference", event.reference(), "type", "DEBIT", "amount",
                                event.amount().amount().toString()));
            }
        }

        if (recipientOpt.isPresent()) {
            UserAccountEntity recipient = recipientOpt.get();
            String creditMsg = String.format("Nadi Pay Alert: Received %s %s from %s. Ref: %s.",
                    event.amount().amount(), event.amount().currency(), senderInfo, event.reference());

            if (Boolean.TRUE.equals(recipient.getSmsNotificationsEnabled())) {
                smsAlertPort.sendSms(recipient.getId(), recipient.getPhoneNumber(), creditMsg,
                        NotificationType.TRANSACTION_CREDIT, event.reference());
            }
            if (Boolean.TRUE.equals(recipient.getPushNotificationsEnabled()) && recipient.getPushToken() != null) {
                pushNotificationPort.sendPush(recipient.getId(), recipient.getPushToken(), "Transfer Received",
                        creditMsg,
                        NotificationType.TRANSACTION_CREDIT, event.reference(),
                        Map.of("reference", event.reference(), "type", "CREDIT", "amount",
                                event.amount().amount().toString()));
            }
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("[NOTIFICATION_LISTENER] Processing payment completion alert for ref: {}", event.reference());

        Optional<UserAccountEntity> userOpt = Optional.empty();
        if (event.customerId() != null) {
            userOpt = userAccountRepository.findByCustomerId(event.customerId())
                    .or(() -> userAccountRepository.findById(event.customerId()));
        }

        if (userOpt.isPresent()) {
            UserAccountEntity user = userOpt.get();
            String title = (event.paymentType() != null) ? event.paymentType().name() + " Successful"
                    : "Payment Completed";
            String msg = String.format("Nadi Pay: Your %s of %s %s was completed successfully. Ref: %s.",
                    event.paymentType(), event.amount().amount(), event.amount().currency(), event.reference());

            NotificationType notifType = switch (event.paymentType()) {
                case TOP_UP -> NotificationType.TOP_UP_SUCCESS;
                case WITHDRAW -> NotificationType.WITHDRAWAL_SUCCESS;
                default -> NotificationType.TRANSACTION_CREDIT;
            };

            if (Boolean.TRUE.equals(user.getSmsNotificationsEnabled())) {
                smsAlertPort.sendSms(user.getId(), user.getPhoneNumber(), msg, notifType, event.reference());
            }
            if (Boolean.TRUE.equals(user.getPushNotificationsEnabled()) && user.getPushToken() != null) {
                pushNotificationPort.sendPush(user.getId(), user.getPushToken(), title, msg, notifType,
                        event.reference(),
                        Map.of("reference", event.reference(), "amount", event.amount().amount().toString()));
            }
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.warn("[NOTIFICATION_LISTENER] Processing payment failed alert for ref: {}", event.reference());

        Optional<UserAccountEntity> userOpt = Optional.empty();
        if (event.customerId() != null) {
            userOpt = userAccountRepository.findByCustomerId(event.customerId())
                    .or(() -> userAccountRepository.findById(event.customerId()));
        }

        if (userOpt.isPresent()) {
            UserAccountEntity user = userOpt.get();
            String msg = String.format("Nadi Pay Alert: Your %s of %s %s failed. Reason: %s. Ref: %s.",
                    event.paymentType(), event.amount().amount(), event.amount().currency(), event.reason(),
                    event.reference());

            if (Boolean.TRUE.equals(user.getSmsNotificationsEnabled())) {
                smsAlertPort.sendSms(user.getId(), user.getPhoneNumber(), msg, NotificationType.PAYMENT_FAILED,
                        event.reference());
            }
            if (Boolean.TRUE.equals(user.getPushNotificationsEnabled()) && user.getPushToken() != null) {
                pushNotificationPort.sendPush(user.getId(), user.getPushToken(), "Transaction Failed", msg,
                        NotificationType.PAYMENT_FAILED, event.reference(),
                        Map.of("reference", event.reference(), "reason", event.reason() != null ? event.reason() : ""));
            }
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserSecurityEvent(UserSecurityEvent event) {
        log.info("[NOTIFICATION_LISTENER] Processing security alert: {} for user: {}", event.action(), event.userId());

        String msg = String.format("Nadi Pay Security Alert: %s on your account. Details: %s.",
                event.action().replace("_", " "), event.details());

        smsAlertPort.sendSms(event.userId(), event.phoneNumber(), msg, NotificationType.SECURITY_ALERT,
                event.eventId().toString());

        Optional<UserAccountEntity> userOpt = (event.userId() != null)
                ? userAccountRepository.findById(event.userId())
                : userAccountRepository.findByPhoneNumber(event.phoneNumber());

        if (userOpt.isPresent() && userOpt.get().getPushToken() != null) {
            pushNotificationPort.sendPush(event.userId(), userOpt.get().getPushToken(), "Security Alert", msg,
                    NotificationType.SECURITY_ALERT, event.eventId().toString(),
                    Map.of("action", event.action(), "details", event.details()));
        }
    }

    private String maskAccountNumber(String acc) {
        if (acc == null || acc.length() < 4)
            return "****";
        return "****" + acc.substring(acc.length() - 4);
    }
}
