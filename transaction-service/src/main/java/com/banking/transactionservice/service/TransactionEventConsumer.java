package com.banking.transactionservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final TransactionService transactionService;

    @KafkaListener(topics = "fraud.check.clean", groupId = "transaction-service-group")
    public void consumeFraudCheckClean(@Payload Map<String, Object> payload) {
        String transactionId = stringValue(payload, "transactionId");
        log.info("Received fraud.check.clean for transaction {}", transactionId);
        try {
            transactionService.handleFraudCheckClean(transactionId);
        } catch (Exception e) {
            log.error("Failed to complete transaction {}: {}", transactionId, e.getMessage());
        }
    }

    @KafkaListener(topics = "verification.required", groupId = "transaction-service-group")
    public void consumeVerificationRequired(@Payload Map<String, Object> payload) {
        String transactionId = stringValue(payload, "transactionId");
        String reason = payload.get("reason") != null
                ? payload.get("reason").toString()
                : "Verification required";

        log.info("Received verification.required for transaction {}", transactionId);
        try {
            transactionService.handleVerificationRequired(transactionId, reason, payload.get("amount"));
        } catch (Exception e) {
            log.error("Failed to mark transaction {} for verification: {}", transactionId, e.getMessage());
        }
    }

    private String stringValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }
}
