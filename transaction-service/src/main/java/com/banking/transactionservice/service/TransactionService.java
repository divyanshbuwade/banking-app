package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    static final String TRANSACTION_OTP_TOPIC = "transaction.otp.generated";
    static final String FRAUD_DETECTED_TOPIC = "fraud.detected";
    static final String OTP_KEY_PREFIX = "verification:otp:";
    static final long OTP_EXPIRY_MINUTES = 5;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        log.info("Initiating transfer from {} to {} amount {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());

        if (request.getSenderAccountNumber().equals(request.getReceiverAccountNumber())) {
            throw new RuntimeException("Sender and receiver accounts must be different");
        }

        accountServiceClient.deductBalance(
                request.getSenderAccountNumber(),
                request.getAmount()
        );

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}", savedTransaction.getId());

        Map<String, Object> event = new HashMap<>();
        event.put("transactionId", savedTransaction.getId());
        event.put("senderAccountNumber", savedTransaction.getSenderAccountNumber());
        event.put("receiverAccountNumber", savedTransaction.getReceiverAccountNumber());
        event.put("amount", savedTransaction.getAmount());
        event.put("description", savedTransaction.getDescription());
        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), event);
        log.info("SAGA step 2 - transaction.initiated published: {}", savedTransaction.getId());

        return mapToResponse(savedTransaction);
    }

    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(findTransaction(transactionId));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository
                .findBySenderAccountNumberOrReceiverAccountNumberOrderByCreatedAtDesc(
                        accountNumber, accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TransactionResponse verifyOtp(String transactionId, String otp) {
        Transaction transaction = findTransaction(transactionId);

        if (transaction.getStatus() != TransactionStatus.PENDING_VERIFICATION) {
            throw new RuntimeException("Transaction is not waiting for OTP verification");
        }

        String otpKey = OTP_KEY_PREFIX + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            log.warn("OTP expired for transaction: {}", transactionId);
            compensateTransaction(transaction, "OTP expired - transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }

        if (!storedOtp.equals(otp)) {
            log.warn("Wrong OTP - blocking account and refunding: {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,
                    "Wrong OTP entered - transaction cancelled, account blocked for security");
            return mapToResponse(transaction);
        }

        log.info("OTP verified - completing transaction: {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransfer(transaction);
        return mapToResponse(transaction);
    }

    @Transactional
    public void handleFraudCheckClean(String transactionId) {
        Transaction transaction = findTransaction(transactionId);
        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            log.warn("Ignoring clean fraud result for transaction {} in status {}",
                    transactionId, transaction.getStatus());
            return;
        }
        completeTransfer(transaction);
    }

    @Transactional
    public void handleVerificationRequired(String transactionId, String reason, Object amount) {
        Transaction transaction = findTransaction(transactionId);
        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            log.warn("Ignoring verification request for transaction {} in status {}",
                    transactionId, transaction.getStatus());
            return;
        }

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String otpKey = OTP_KEY_PREFIX + transactionId;
        redisTemplate.opsForValue().set(otpKey, otp, OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

        transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);

        Map<String, Object> otpEvent = new HashMap<>();
        otpEvent.put("transactionId", transactionId);
        otpEvent.put("accountNumber", transaction.getSenderAccountNumber());
        otpEvent.put("reason", reason);
        otpEvent.put("otp", otp);
        otpEvent.put("amount", amount != null ? amount : transaction.getAmount());
        kafkaTemplate.send(TRANSACTION_OTP_TOPIC, transactionId, otpEvent);

        log.info("OTP generated for transaction {} (demo OTP: {})", transactionId, otp);
    }

    private void completeTransfer(Transaction transaction) {
        accountServiceClient.creditBalance(
                transaction.getReceiverAccountNumber(),
                transaction.getAmount()
        );
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setFailureReason(null);
        transactionRepository.save(transaction);

        Map<String, Object> completed = new HashMap<>();
        completed.put("transactionId", transaction.getId());
        completed.put("senderAccountNumber", transaction.getSenderAccountNumber());
        completed.put("receiverAccountNumber", transaction.getReceiverAccountNumber());
        completed.put("amount", transaction.getAmount());
        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completed);
        log.info("SAGA completed - receiver credited: {}", transaction.getId());
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}",
                transaction.getSenderAccountNumber(),
                transaction.getAmount());

        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount()
        );

        transaction.setStatus(TransactionStatus.REFUNDED);
        transaction.setFailureReason(reason);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("accountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);
        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);
        log.info("SAGA compensating refund published: {}", transaction.getId());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);
        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("fraud.detected published - account: {} will be blocked",
                transaction.getSenderAccountNumber());
        compensateTransaction(transaction, reason);
    }

    private Transaction findTransaction(String transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setSenderAccountNumber(transaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setStatus(transaction.getStatus());
        response.setDescription(transaction.getDescription());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setFailureReason(transaction.getFailureReason());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());
        return response;
    }
}
