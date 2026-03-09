## AWS Application Load Balancer Setup
- **Region (example)**: `us-west-2`
- **Chat servers**: `server-v2` JAR on **port 8080**
- **Consumers**: consumer JAR on **port 8081** (not behind ALB)
- **Queue**: RabbitMQ (default port 5672)

Overall architecture:

Client → **ALB** → `[Server1, Server2, Server3, Server4]` → **RabbitMQ Queue** → **Consumers**

---

## 1. Prerequisites

- Four EC2 instances running `server-v2` in the **same VPC**.
- One instances running **RabbitMQ**.
- One instances running the **consumer**.
- Security groups:
  - **ALB SG**: allow inbound TCP **80** from the internet.
  - **Server SG**: allow inbound TCP **8080** from the ALB SG.
  - **Consumers → RabbitMQ**: allow TCP **5672** (or your chosen port).

You will need:

- `VPC_ID`
- Two public subnet IDs in that VPC: `SUBNET_1`, `SUBNET_2` (different AZs).
- A security group ID for the ALB: `ALB_SG_ID`.
- The **instance IDs** of the four chat servers: `INSTANCE_1` … `INSTANCE_4`.

---

## 2. Helper Script: `create-chat-alb.sh`

The script `deployment/create-chat-alb.sh` automates the ALB and target group setup.

### 2.1 Usage

From the repo root:

```bash
cd deployment
./create-chat-alb.sh \
  <region> \
  <vpc-id> \
  <subnet-1-id> \
  <subnet-2-id> \
  <alb-sg-id> \
  <instance-1-id> \
  <instance-2-id> \
  <instance-3-id> \
  <instance-4-id>
```

The script will:

- Create an **HTTP target group** on **port 8080** with `/health` checks:
  - Interval **30 seconds**
  - Timeout **5 seconds**
  - Healthy threshold **2**
  - Unhealthy threshold **3**
- Enable **sticky sessions** (required for WebSocket affinity) using:
  - `lb_cookie` type
  - Cookie duration **3600 seconds** (1 hour)
- Create an **internet-facing Application Load Balancer**:
  - Listener: **HTTP :80**
  - Idle timeout: **120 seconds** (>\= 60 seconds as required).
- Register the four WebSocket server instances as targets.

At the end it prints:

- ALB **DNS name**
- Target group **ARN**