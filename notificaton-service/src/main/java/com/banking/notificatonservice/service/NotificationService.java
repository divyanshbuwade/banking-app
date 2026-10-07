package com.banking.notificatonservice.service;

import com.banking.notificatonservice.model.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class NotificationService {

    private final Map<String, List<Notification>> notifications = new ConcurrentHashMap<>();

    @KafkaListener(topics = "transaction.otp.generated", groupId = "notification-service-group")
    public void consumeOtpGenerated(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = stringValue(payload, "accountNumber");
            String otp = stringValue(payload, "otp");
            String amount = stringValue(payload, "amount");
            String reason = stringValue(payload, "reason");
            sendAlert(
                    accountNumber,
                    "TRANSACTION VERIFICATION REQUIRED",
                    String.format(
                            "Suspicious activity detected. Reason: %s. A transaction of %s is pending. OTP: %s (valid 5 minutes).",
                            reason, amount, otp));
        } catch (Exception e) {
            log.error("Error sending OTP notification: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "transaction.completed", groupId = "notification-service-group")
    public void consumeTransactionCompleted(@Payload Map<String, Object> payload) {
        try {
            String senderAccount = stringValue(payload, "senderAccountNumber");
            String receiverAccount = stringValue(payload, "receiverAccountNumber");
            String amount = stringValue(payload, "amount");
            sendAlert(senderAccount, "DEBIT ALERT",
                    String.format("%s debit from account %s", amount, senderAccount));
            sendAlert(receiverAccount, "CREDIT ALERT",
                    String.format("%s credit to account %s", amount, receiverAccount));
        } catch (Exception e) {
            log.error("Error sending transaction notification: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.detected", groupId = "notification-service-group")
    public void consumeFraudDetected(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = stringValue(payload, "accountNumber");
            String reason = stringValue(payload, "reason");
            sendAlert(
                    accountNumber,
                    "SUSPICIOUS ACTIVITY DETECTED",
                    String.format("Account %s has been blocked. Reason: %s. Contact support.",
                            accountNumber, reason));
        } catch (Exception e) {
            log.error("Error sending fraud notification: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "transaction.refunded", groupId = "notification-service-group")
    public void consumeTransactionRefunded(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = stringValue(payload, "accountNumber");
            if (accountNumber == null) {
                accountNumber = stringValue(payload, "senderAccountNumber");
            }
            String amount = stringValue(payload, "amount");
            String reason = stringValue(payload, "reason");
            sendAlert(
                    accountNumber,
                    "REFUND PROCESSED",
                    String.format("Transaction of %s was cancelled. Reason: %s. Amount refunded to %s.",
                            amount, reason, accountNumber));
        } catch (Exception e) {
            log.error("Error sending refund notification: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.completed", groupId = "notification-service-group")
    public void consumePaymentCompleted(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = stringValue(payload, "accountNumber");
            String amount = stringValue(payload, "amount");
            String paymentId = stringValue(payload, "razorpayPaymentId");
            sendAlert(
                    accountNumber,
                    "PAYMENT SUCCESSFUL",
                    String.format("Deposit of %s completed. Payment ID: %s", amount, paymentId));
        } catch (Exception e) {
            log.error("Error sending payment notification: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "notification-service-group")
    public void consumePaymentFailed(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = stringValue(payload, "accountNumber");
            String amount = stringValue(payload, "amount");
            sendAlert(
                    accountNumber,
                    "PAYMENT FAILED",
                    String.format("Payment of %s could not be processed.", amount));
        } catch (Exception e) {
            log.error("Error sending payment failure notification: {}", e.getMessage());
        }
    }

    public List<Notification> getNotifications(String accountNumber) {
        List<Notification> list = notifications.get(accountNumber);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    private String stringValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private void sendAlert(String accountNumber, String subject, String message) {
        log.info("NOTIFICATION | account={} | {} | {}", accountNumber, subject, message);
        if (accountNumber == null) {
            return;
        }
        Notification notification = new Notification(
                UUID.randomUUID().toString(),
                accountNumber,
                subject,
                message,
                LocalDateTime.now()
        );
        notifications.computeIfAbsent(accountNumber, key -> new ArrayList<>());
        List<Notification> list = notifications.get(accountNumber);
        synchronized (list) {
            list.add(0, notification);
        }
    }
}
