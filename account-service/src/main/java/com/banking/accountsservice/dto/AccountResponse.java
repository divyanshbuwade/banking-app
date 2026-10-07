package com.banking.accountsservice.dto;

import com.banking.accountsservice.entity.AccountStatus;
import com.banking.accountsservice.entity.AccountType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountResponse {

    private String id;
    private String accountHolderName;
    private String accountNumber;
    private String email;
    private String phone;
    private AccountType accountType;
    private AccountStatus status;
    private BigDecimal balance;
    private BigDecimal dailyTranscationLimit;
    private LocalDateTime createdAt;
}
