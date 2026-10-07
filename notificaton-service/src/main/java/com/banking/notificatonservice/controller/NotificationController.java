package com.banking.notificatonservice.controller;

import com.banking.notificatonservice.model.Notification;
import com.banking.notificatonservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/{accountNumber}")
    public ResponseEntity<List<Notification>> getNotifications(@PathVariable String accountNumber) {
        return ResponseEntity.ok(notificationService.getNotifications(accountNumber));
    }
}
