package com.banking.transactionservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferRequest {

    @NotBlank
    private String senderAccountNumber;

    @NotBlank
    private String receiverAccountNumber;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private String description;
}
