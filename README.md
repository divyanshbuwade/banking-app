# NovaBank — Microservice Banking System

A production-oriented Spring Boot microservices banking application demonstrating API Gateway routing, event-driven transaction processing, Kafka-based Saga orchestration, fraud detection, payment processing, and notifications.

---

## 🏗️ Architecture

```text
                         ┌─────────────────────┐
                         │      Frontend       │
                         │    Browser UI       │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │    API Gateway      │
                         │       :8080         │
                         └───────┬─┬─┬────────┘
                                 │ │ │
              ┌──────────────────┘ │ └──────────────────┐
              ▼                    ▼                    ▼
      ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
      │   Account    │    │ Transaction  │    │   Payment    │
      │   :8081      │    │   :8082      │    │   :8083      │
      └──────┬───────┘    └──────┬───────┘    └──────────────┘
             │                   │
             │                   ▼
             │          ┌─────────────────┐
             │          │ Kafka / Saga     │
             │          └───────┬─────────┘
             │                  │
             ▼                  ▼
      ┌──────────────┐   ┌──────────────┐
      │ Fraud        │   │ Notification │
      │ :8085        │   │ :8084        │
      └──────────────┘   └──────────────┘

Infrastructure:
- Kafka + Zookeeper
- Redis
- MySQL
- H2
