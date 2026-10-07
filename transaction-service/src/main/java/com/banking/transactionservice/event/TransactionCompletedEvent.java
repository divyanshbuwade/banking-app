package com.banking.transactionservice.event;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class TransactionCompletedEvent {
    private String transactionId;
    private String senderAccountNumber;
    private String receiverAccountNumber;
    private String description;
    private String amount;
}
