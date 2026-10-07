# Microservice Banking App

NovaBank is a Spring Boot microservices banking system with an API gateway, Kafka-based transfer saga, fraud checks, demo deposits, and a browser UI.

## Services

| Service | Port | Role |
|---|---|---|
| API Gateway | 8080 | Entry point, CORS, Redis rate limiting |
| Account Service | 8081 | Accounts, balances, blocks |
| Transaction Service | 8082 | Transfers, OTP verification, saga orchestration |
| Payment Service | 8083 | Deposits (Razorpay or demo simulate) |
| Notification Service | 8084 | Kafka alerts |
| Fraud Detection Service | 8085 | Velocity / amount / balance checks |

## Transfer saga

1. Deduct sender balance.
2. Publish `transaction.initiated`.
3. Fraud service publishes `fraud.check.clean` or `verification.required`.
4. Clean transfers credit the receiver. Suspicious transfers wait for OTP.
5. Wrong OTP publishes `fraud.detected` (account blocked) and refunds the sender.

## Run locally

1. Start infrastructure:

```bash
docker compose up -d
```

2. Wait until MySQL, Redis, Zookeeper, and Kafka are healthy, then start each service with Maven from its folder:

```bash
mvn spring-boot:run
```

Start order: account-service, transaction-service, payment-service, notificaton-service, fraud-detection-service, api-gateway.

3. Open `frontend/index.html` in a browser (or serve that folder with any static file server). The UI talks to `http://localhost:8080`.

MySQL credentials used by the services: `root` / `root`. Databases are created automatically.

Deposits work without Razorpay keys via **Create and complete deposit**. Set `RAZORPAY_KEY_SECRET` if you want live Razorpay orders.
