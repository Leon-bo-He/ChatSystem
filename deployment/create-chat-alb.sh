#!/usr/bin/env bash
# Usage:
#   ./create-chat-alb.sh \
#     <region> \
#     <vpc-id> \
#     <subnet-1-id> \
#     <subnet-2-id> \
#     <alb-sg-id> \
#     <instance-1-id> \
#     <instance-2-id> \
#     <instance-3-id> \
#     <instance-4-id>
#
# Requirements implemented:
# - Target group with HTTP health checks on /health
#   - Interval: 30s, Timeout: 5s, Healthy: 2, Unhealthy: 3
# - Sticky sessions (lb_cookie) with 1 hour duration
# - ALB with idle timeout > 60s (set to 120s)
# - Listener on HTTP:80 forwarding to the chat target group

set -euo pipefail

if [ "$#" -ne 9 ]; then
  echo "Usage: $0 <region> <vpc-id> <subnet-1-id> <subnet-2-id> <alb-sg-id> <instance-1-id> <instance-2-id> <instance-3-id> <instance-4-id>" >&2
  exit 1
fi

REGION="$1"
VPC_ID="$2"
SUBNET_1="$3"
SUBNET_2="$4"
ALB_SG_ID="$5"
INSTANCE_1="$6"
INSTANCE_2="$7"
INSTANCE_3="$8"
INSTANCE_4="$9"

AWS="aws --region ${REGION}"

echo "Creating target group for chat WebSocket servers..."
TG_NAME="chat-websocket-targets-$(date +%s)"

TG_ARN="$(
  ${AWS} elbv2 create-target-group \
    --name "${TG_NAME}" \
    --protocol HTTP \
    --port 8080 \
    --vpc-id "${VPC_ID}" \
    --target-type instance \
    --health-check-protocol HTTP \
    --health-check-path "/health" \
    --health-check-interval-seconds 30 \
    --health-check-timeout-seconds 5 \
    --healthy-threshold-count 2 \
    --unhealthy-threshold-count 3 \
    --query 'TargetGroups[0].TargetGroupArn' \
    --output text
)"

echo "Created target group: ${TG_NAME}"
echo "Target group ARN: ${TG_ARN}"

echo "Enabling sticky sessions (lb_cookie) with 1 hour duration..."
${AWS} elbv2 modify-target-group-attributes \
  --target-group-arn "${TG_ARN}" \
  --attributes \
    Key=stickiness.enabled,Value=true \
    Key=stickiness.type,Value=lb_cookie \
    Key=stickiness.lb_cookie.duration_seconds,Value=3600 >/dev/null

echo "Registering targets (chat server instances)..."
${AWS} elbv2 register-targets \
  --target-group-arn "${TG_ARN}" \
  --targets \
    Id="${INSTANCE_1}",Port=8080 \
    Id="${INSTANCE_2}",Port=8080 \
    Id="${INSTANCE_3}",Port=8080 \
    Id="${INSTANCE_4}",Port=8080

echo "Creating Application Load Balancer..."
ALB_NAME="chat-websocket-alb-$(date +%s)"

ALB_ARN="$(
  ${AWS} elbv2 create-load-balancer \
    --name "${ALB_NAME}" \
    --type application \
    --scheme internet-facing \
    --subnets "${SUBNET_1}" "${SUBNET_2}" \
    --security-groups "${ALB_SG_ID}" \
    --query 'LoadBalancers[0].LoadBalancerArn' \
    --output text
)"

ALB_DNS="$(
  ${AWS} elbv2 describe-load-balancers \
    --load-balancer-arns "${ALB_ARN}" \
    --query 'LoadBalancers[0].DNSName' \
    --output text
)"

echo "Created ALB: ${ALB_NAME}"
echo "ALB ARN: ${ALB_ARN}"
echo "ALB DNS name: ${ALB_DNS}"

echo "Setting ALB idle timeout to 120 seconds (>= 60s requirement)..."
${AWS} elbv2 modify-load-balancer-attributes \
  --load-balancer-arn "${ALB_ARN}" \
  --attributes Key=idle_timeout.timeout_seconds,Value=120 >/dev/null

echo "Creating HTTP:80 listener forwarding to target group..."
LISTENER_ARN="$(
  ${AWS} elbv2 create-listener \
    --load-balancer-arn "${ALB_ARN}" \
    --protocol HTTP \
    --port 80 \
    --default-actions Type=forward,TargetGroupArn="${TG_ARN}" \
    --query 'Listeners[0].ListenerArn' \
    --output text
)"

echo "Created listener ARN: ${LISTENER_ARN}"

cat <<EOF

ALB and target group created successfully.

- ALB name: ${ALB_NAME}
- ALB DNS name: ${ALB_DNS}
- ALB ARN: ${ALB_ARN}
- Listener ARN: ${LISTENER_ARN}
- Target group name: ${TG_NAME}
- Target group ARN: ${TG_ARN}

You can now:
- Point your WebSocket clients to: ws://${ALB_DNS}/chat/{roomId}
- Confirm health checks at:       http://${ALB_DNS}/health

This configuration satisfies:
- Health checks on /health (30s interval, 5s timeout, healthy=2, unhealthy=3)
- Sticky sessions using lb_cookie with 1 hour duration
- WebSocket support via HTTP:80 with idle timeout of 120s (> 60s)
- Architecture: Client -> ALB -> [Server1-4] -> Queue -> Consumers
EOF

