#!/bin/bash
# System Health Check Script
# Checks health of all Assignment 2 components

echo "========================================="
echo "CS6650 Assignment 2 - System Health Check"
echo "Time: $(date)"
echo "========================================="
echo ""

# Component IPs
RABBITMQ_IP="52.36.14.243"
CONSUMER_IP="54.70.215.241"
SERVERS=("44.251.37.173" "54.213.225.202" "44.246.30.231" "35.87.47.169")
ALB_DNS="alb-a2-631558146.us-west-2.elb.amazonaws.com"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. Check RabbitMQ
echo "1. RabbitMQ Health (${RABBITMQ_IP})"
RABBITMQ_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://${RABBITMQ_IP}:15672 --connect-timeout 5)
if [ "$RABBITMQ_STATUS" == "200" ]; then
    echo -e "   ${GREEN}✓${NC} RabbitMQ Management: Accessible (HTTP $RABBITMQ_STATUS)"

    # Get queue stats
    QUEUE_INFO=$(curl -s -u guest:guest http://${RABBITMQ_IP}:15672/api/queues 2>/dev/null | python3 -c "import sys, json; data=json.load(sys.stdin); print(f'Queues: {len(data)}')" 2>/dev/null)
    echo "   ${GREEN}✓${NC} $QUEUE_INFO"
else
    echo -e "   ${RED}✗${NC} RabbitMQ Management: Unreachable (HTTP $RABBITMQ_STATUS)"
fi
echo ""

# 2. Check Consumer
echo "2. Consumer Health (${CONSUMER_IP})"
CONSUMER_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://${CONSUMER_IP}:8080/health --connect-timeout 5)
if [ "$CONSUMER_STATUS" == "200" ]; then
    echo -e "   ${GREEN}✓${NC} Consumer: Healthy (HTTP $CONSUMER_STATUS)"

    # Get detailed health
    HEALTH_DATA=$(curl -s http://${CONSUMER_IP}:8080/health 2>/dev/null)
    PROCESSED=$(echo "$HEALTH_DATA" | python3 -c "import sys, json; print(json.load(sys.stdin).get('metrics', {}).get('messagesProcessed', 'N/A'))" 2>/dev/null)
    BROADCASTS=$(echo "$HEALTH_DATA" | python3 -c "import sys, json; print(json.load(sys.stdin).get('metrics', {}).get('broadcastsSucceeded', 'N/A'))" 2>/dev/null)
    echo "   Messages Processed: $PROCESSED"
    echo "   Broadcasts Succeeded: $BROADCASTS"
else
    echo -e "   ${RED}✗${NC} Consumer: Unreachable (HTTP $CONSUMER_STATUS)"
fi
echo ""

# 3. Check Servers
echo "3. Server Instances"
for i in "${!SERVERS[@]}"; do
    SERVER="${SERVERS[$i]}"
    SERVER_NUM=$((i + 1))
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://${SERVER}:8080/chat-server/ --connect-timeout 5)
    if [ "$STATUS" == "200" ]; then
        echo -e "   ${GREEN}✓${NC} Server $SERVER_NUM ($SERVER): Running (HTTP $STATUS)"
    else
        echo -e "   ${RED}✗${NC} Server $SERVER_NUM ($SERVER): Down (HTTP $STATUS)"
    fi
done
echo ""

# 4. Check ALB
echo "4. Application Load Balancer"
ALB_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://${ALB_DNS}:8080/chat-server/ --connect-timeout 5)
if [ "$ALB_STATUS" == "200" ]; then
    echo -e "   ${GREEN}✓${NC} ALB: Accessible (HTTP $ALB_STATUS)"
    echo "   DNS: $ALB_DNS"
else
    echo -e "   ${RED}✗${NC} ALB: Unreachable (HTTP $ALB_STATUS)"
fi
echo ""

# Summary
echo "========================================="
echo "Summary"
echo "========================================="
TOTAL_HEALTHY=0
TOTAL_COMPONENTS=6  # RabbitMQ + Consumer + 4 Servers

[ "$RABBITMQ_STATUS" == "200" ] && TOTAL_HEALTHY=$((TOTAL_HEALTHY + 1))
[ "$CONSUMER_STATUS" == "200" ] && TOTAL_HEALTHY=$((TOTAL_HEALTHY + 1))
for i in "${!SERVERS[@]}"; do
    SERVER="${SERVERS[$i]}"
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://${SERVER}:8080/chat-server/ --connect-timeout 5)
    [ "$STATUS" == "200" ] && TOTAL_HEALTHY=$((TOTAL_HEALTHY + 1))
done

HEALTH_PERCENTAGE=$((TOTAL_HEALTHY * 100 / TOTAL_COMPONENTS))

if [ $HEALTH_PERCENTAGE -eq 100 ]; then
    echo -e "${GREEN}System Status: ALL HEALTHY${NC} ($TOTAL_HEALTHY/$TOTAL_COMPONENTS components)"
elif [ $HEALTH_PERCENTAGE -ge 80 ]; then
    echo -e "${YELLOW}System Status: DEGRADED${NC} ($TOTAL_HEALTHY/$TOTAL_COMPONENTS components)"
else
    echo -e "${RED}System Status: CRITICAL${NC} ($TOTAL_HEALTHY/$TOTAL_COMPONENTS components)"
fi
echo ""