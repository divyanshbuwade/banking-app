package com.banking.notificatonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    private String id;
    private String accountNumber;
    private String subject;
    private String message;
    private LocalDateTime createdAt;
}
