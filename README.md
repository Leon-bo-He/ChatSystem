# Chat System

A distributed WebSocket chat system with RabbitMQ message broker, AWS ALB load balancing, and a dedicated consumer for broadcasting messages to clients.

## Project Structure

| Module | Description |
|--------|-------------|
| **server-v2** | WebSocket chat server – receives client messages and publishes to RabbitMQ |
| **consumer** | RabbitMQ consumer – pulls messages from RabbitMQ and broadcasts to WebSocket clients |
| **deployment** | AWS ALB setup scripts and configuration |
| **monitoring** | Health check and RabbitMQ monitoring scripts |
| **docs** | Architecture documentation |
| **results** | CSV data and throughput charts from load testing |

## Architecture Overview

```
[WebSocket Clients] → [AWS ALB] → [Servers (server-v2)] → [RabbitMQ] → [Consumer] → [WebSocket Broadcast]
```

## Build

```bash
mvn clean package
```

## Running from an IDE

- **Server (server-v2):** Run `life.hebo.ChatServerApplication` in the `server-v2` module (port 8080).
- **Consumer:** Run the main class in the `consumer` module (port 8081).

## Quick Start (Local)

1. Start RabbitMQ (e.g. `docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management`).
2. Run the server: `mvn -pl server-v2 spring-boot:run`
3. Run the consumer: `mvn -pl consumer spring-boot:run`
4. Connect clients to the server WebSocket endpoint (via ALB in production, or directly to server/consumer for local testing).