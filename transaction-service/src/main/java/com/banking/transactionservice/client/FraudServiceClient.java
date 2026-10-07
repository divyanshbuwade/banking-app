package com.banking.transactionservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "fraud-detection-service", url = "${fraud.service.url}")
public interface FraudServiceClient {

    @PostMapping("/api/v1/fraud/check")
    Map<String, Object> check(@RequestBody Map<String, Object> payload);
}
