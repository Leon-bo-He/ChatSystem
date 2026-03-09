RABBITMQ_HOST="52.36.14.243"
RABBITMQ_PORT="15672"
USERNAME="admin"
PASSWORD="admin"

echo "========================================="
echo "RabbitMQ Queue Monitoring"
echo "Host: $RABBITMQ_HOST:$RABBITMQ_PORT"
echo "========================================="
echo ""

# Get queue statistics
echo "Fetching queue statistics..."
QUEUE_DATA=$(curl -s -u $USERNAME:$PASSWORD http://${RABBITMQ_HOST}:${RABBITMQ_PORT}/api/queues 2>/dev/null)

if [ -z "$QUEUE_DATA" ]; then
    echo "❌ Failed to connect to RabbitMQ Management API"
    exit 1
fi

# Parse and display queue information
echo "$QUEUE_DATA" | python3 << 'EOFPYTHON'
import json
import sys

data = json.load(sys.stdin)

print(f"{'Queue Name':<15} {'Messages':<10} {'Ready':<10} {'Unacked':<10} {'Rate (msg/s)':<12}")
print("=" * 60)

total_messages = 0
total_ready = 0
total_unacked = 0

for queue in data:
    name = queue.get('name', 'N/A')
    messages = queue.get('messages', 0)
    ready = queue.get('messages_ready', 0)
    unacked = queue.get('messages_unacknowledged', 0)

    # Get message rate
    rate_details = queue.get('messages_details', {})
    rate = rate_details.get('rate', 0)

    total_messages += messages
    total_ready += ready
    total_unacked += unacked

    print(f"{name:<15} {messages:<10} {ready:<10} {unacked:<10} {rate:<12.2f}")

print("=" * 60)
print(f"{'TOTAL':<15} {total_messages:<10} {total_ready:<10} {total_unacked:<10}")
print()
print(f"Queue Health:")
if total_messages < 1000:
    print("  ✓ Queue depth normal (< 1000)")
else:
    print(f"  ⚠ Queue depth high ({total_messages} messages)")

if total_unacked < 100:
    print("  ✓ Unacked messages normal (< 100)")
else:
    print(f"  ⚠ High unacked messages ({total_unacked})")
EOFPYTHON

echo ""
echo "========================================="
echo "Connection Information"
echo "========================================="

# Get connection info
CONN_DATA=$(curl -s -u $USERNAME:$PASSWORD http://${RABBITMQ_HOST}:${RABBITMQ_PORT}/api/connections 2>/dev/null)
CONN_COUNT=$(echo "$CONN_DATA" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null)

echo "Active Connections: $CONN_COUNT"

echo ""
echo "To view in browser:"
echo "  http://${RABBITMQ_HOST}:${RABBITMQ_PORT}"
echo "  Username: $USERNAME"
echo "  Password: $PASSWORD"