# Chat System

A distributed WebSocket chat stack: clients talk to Spring Boot servers behind an AWS ALB; messages go through RabbitMQ; a consumer tier pulls from the queue, persists to PostgreSQL, and broadcasts to WebSocket subscribers.

## System structure

The repository is a Maven multi-module project plus supporting assets:

| Part | Role |
|------|------|
| **server-v2** | WebSocket ingress: validates messages, optional HTTP rate limiting, publishes to RabbitMQ, and exposes `/api/metrics` for room/user statistics backed by the same database. |
| **consumer-v3** | RabbitMQ consumer pool: consumes queue messages, fans out to WebSocket clients, and batches writes through `MessagePersistenceService` into PostgreSQL (configurable workers, prefetch, DLQ, persistence batching). |
| **database** | PostgreSQL DDL: `messages` table, indexes, and materialized views for analytics-style queries. |
| **load-tests** | Python load and endurance scripts; result charts live under `load-tests/results/`. |
| **monitoring** | Helper scripts to display metrics, batch analysis, and DB-related evidence. |

**End-to-end path:**

```
[WebSocket clients] → [AWS ALB] → [server-v2] → [RabbitMQ] → [consumer-v3] → WebSocket broadcast
                                      ↓                                              ↓
                                 PostgreSQL ←────────────────────────────────────────┘
```

Both JVM services use Spring JDBC against PostgreSQL; RabbitMQ is required for the publish/consume path.

## Changes from the HW2 Git baseline

Compared to the earlier HW2 assignment (single-path chat with a basic producer/consumer split), this tree extends the design in a few concrete ways:

- **Consumer:** replaced the minimal consumer with **consumer-v3**—a **pooled** consumer model, **batched persistence** with retry/backpressure-style settings, optional **dead-letter** handling, and **Actuator** health indicators (including RabbitMQ).
- **Data plane:** added a **PostgreSQL** schema under `database/` and **JDBC persistence** on the consumer; the server participates in **metrics** queries over stored message history.
- **Ops and evaluation:** added **`load-tests`** and **`monitoring`** automation rather than only manual checks.

Deployment-specific files (for example ALB or instance setup) are maintained outside this repo if your course uses a separate infra assignment.

## Build

```bash
mvn clean package
```

## Running from an IDE

- **Server:** `life.hebo.ChatServerApplication` in **server-v2** (default HTTP port **8080**).
- **Consumer:** `life.hebo.ConsumerApplication` in **consumer-v3**. If you run server and consumer on one machine, give the consumer another port (for example `--server.port=8081`).

## Quick start (local)

1. Start **PostgreSQL** and apply `database/schema.sql` (or your migration equivalent). Align `DB_URL` / `DB_USER` / `DB_PASSWORD` with `server-v2` and `consumer-v3` `application.properties`.
2. Start **RabbitMQ** (for example `docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management`).
3. Run the server: `mvn -pl server-v2 spring-boot:run`
4. Run the consumer: `mvn -pl consumer-v3 spring-boot:run` (add `-Dspring-boot.run.arguments=--server.port=8081` if you need a non-default port).
5. Point WebSocket clients at the server URL (through the ALB in production, or directly at the server for local runs).
