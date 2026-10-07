package com.banking.accountsservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    @KafkaListener(topics = "fraud.detected", groupId = "account-service-group")
    public void consumeFraudDetected(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = (String) payload.get("accountNumber");
            if (accountNumber == null) {
                log.warn("Skipping fraud.detected - missing accountNumber: {}", payload);
                return;
            }
            log.info("Fraud detected - blocking account: {}", accountNumber);
            accountService.blockAccount(accountNumber);
        } catch (Exception e) {
            log.error("Error blocking account: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.completed", groupId = "account-service-group")
    public void consumePaymentCompleted(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = (String) payload.get("accountNumber");
            if (accountNumber == null || payload.get("amount") == null) {
                log.warn("Skipping payment.completed - missing account or amount: {}", payload);
                return;
            }
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());
            log.info("Crediting deposit to account: {} amount: {}", accountNumber, amount);
            accountService.creditBalance(accountNumber, amount);
        } catch (Exception e) {
            log.error("Error crediting payment: {}", e.getMessage());
        }
    }
}
