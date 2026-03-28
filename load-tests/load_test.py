#!/usr/bin/env python3

import pika
import json
import time
import sys
import argparse
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed
import requests

class LoadTester:
    def __init__(self, rabbitmq_host, consumer_host, num_rooms=20):
        self.rabbitmq_host = rabbitmq_host
        self.consumer_host = consumer_host
        self.num_rooms = num_rooms
        self.stats = {
            'sent': 0,
            'failed': 0,
            'start_time': None,
            'end_time': None
        }

    def create_connection(self):
        """Create a RabbitMQ connection"""
        credentials = pika.PlainCredentials('guest', 'guest')
        parameters = pika.ConnectionParameters(
            host=self.rabbitmq_host,
            port=5672,
            virtual_host='/',
            credentials=credentials,
            connection_attempts=3,
            retry_delay=2
        )
        return pika.BlockingConnection(parameters)

    def send_messages_worker(self, room_id, num_messages, worker_id):
        """Worker thread to send messages for a specific room"""
        try:
            connection = self.create_connection()
            channel = connection.channel()

            messages_sent = 0
            messages_failed = 0

            for i in range(num_messages):
                try:
                    message = {
                        "messageId": f"msg_{room_id}_{worker_id}_{i}",
                        "roomId": str(room_id),
                        "userId": f"user_{i % 1000}",
                        "username": f"TestUser{i % 1000}",
                        "message": f"Load test message {i}",
                        "timestamp": datetime.utcnow().isoformat() + "Z",
                        "messageType": "chat"
                    }

                    channel.basic_publish(
                        exchange='chat.exchange',
                        routing_key=f'room.{room_id}',
                        body=json.dumps(message),
                        properties=pika.BasicProperties(
                            delivery_mode=2,  # Make message persistent
                            content_type='application/json'
                        )
                    )
                    messages_sent += 1

                    if (i + 1) % 1000 == 0:
                        print(f"  Worker {worker_id} (Room {room_id}): {i + 1}/{num_messages} sent")

                except Exception as e:
                    messages_failed += 1
                    if messages_failed % 100 == 0:
                        print(f"  Worker {worker_id}: {messages_failed} failures")

            connection.close()
            return (room_id, worker_id, messages_sent, messages_failed)

        except Exception as e:
            print(f"Worker {worker_id} (Room {room_id}) failed: {e}")
            return (room_id, worker_id, 0, num_messages)

    def run_load_test(self, total_messages, num_workers=20):
        """Run the load test"""
        print("=" * 60)
        print("CS6650 Assignment 3 - Load Test")
        print("=" * 60)
        print(f"Configuration:")
        print(f"  Total Messages: {total_messages:,}")
        print(f"  Rooms: {self.num_rooms}")
        print(f"  Workers: {num_workers}")
        print(f"  Messages per worker: {total_messages // num_workers:,}")
        print(f"  RabbitMQ: {self.rabbitmq_host}:5672")
        print(f"  Consumer: {self.consumer_host}:8080")
        print("=" * 60)
        print()

        messages_per_worker = total_messages // num_workers
        self.stats['start_time'] = time.time()

        print(f"Starting load test at {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print()

        # Distribute messages across workers and rooms
        with ThreadPoolExecutor(max_workers=num_workers) as executor:
            futures = []

            for worker_id in range(num_workers):
                room_id = (worker_id % self.num_rooms) + 1
                futures.append(
                    executor.submit(
                        self.send_messages_worker,
                        room_id,
                        messages_per_worker,
                        worker_id
                    )
                )

            # Wait for all workers to complete
            for future in as_completed(futures):
                room_id, worker_id, sent, failed = future.result()
                self.stats['sent'] += sent
                self.stats['failed'] += failed
                print(f"Worker {worker_id} (Room {room_id}) completed: {sent} sent, {failed} failed")

        self.stats['end_time'] = time.time()
        duration = self.stats['end_time'] - self.stats['start_time']

        print()
        print("=" * 60)
        print("Load Test Results")
        print("=" * 60)
        print(f"Messages sent: {self.stats['sent']:,}")
        print(f"Messages failed: {self.stats['failed']:,}")
        print(f"Duration: {duration:.2f} seconds")
        print(f"Throughput: {self.stats['sent'] / duration:.2f} messages/second")
        print("=" * 60)
        print()

        # Wait for consumer to process
        wait_time = 60
        print(f"Waiting {wait_time} seconds for consumer to process all messages...")
        time.sleep(wait_time)

        # Call Metrics API
        self.get_metrics()

    def get_metrics(self):
        """Call the Metrics API and display results"""
        print()
        print("=" * 60)
        print("Calling Metrics API...")
        print("=" * 60)

        try:
            response = requests.get(f"http://{self.consumer_host}:8080/metrics", timeout=30)
            response.raise_for_status()

            metrics = response.json()

            # Save to file
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            filename = f"load-test-results/metrics_{timestamp}.json"

            import os
            os.makedirs('load-test-results', exist_ok=True)

            with open(filename, 'w') as f:
                json.dump(metrics, f, indent=2)

            print(f"Metrics saved to: {filename}")
            print()
            print("Metrics Summary:")
            print("-" * 60)

            # Display core metrics
            if 'coreQueries' in metrics:
                core = metrics['coreQueries']
                print(f"Total Messages in DB: {core.get('totalMessages', 0):,}")
                print(f"Messages per Room: {len(core.get('messagesPerRoom', []))} rooms")
                print(f"User Participation: {len(core.get('userParticipation', []))} entries")

            # Display analytics
            if 'analyticsQueries' in metrics:
                analytics = metrics['analyticsQueries']
                print(f"Top Active Users: {len(analytics.get('topActiveUsers', []))}")
                print(f"Top Active Rooms: {len(analytics.get('topActiveRooms', []))}")

            print("-" * 60)
            print()
            print("Full metrics JSON:")
            print(json.dumps(metrics, indent=2))

        except Exception as e:
            print(f"Error calling Metrics API: {e}")

def main():
    parser = argparse.ArgumentParser(description='CS6650 Assignment 3 Load Test')
    parser.add_argument('--messages', type=int, default=500000,
                        help='Total number of messages to send (default: 500000)')
    parser.add_argument('--workers', type=int, default=20,
                        help='Number of concurrent workers (default: 20)')
    parser.add_argument('--rabbitmq-host', type=str, default='35.150.106.216',
                        help='RabbitMQ host (default: 35.150.106.216)')
    parser.add_argument('--consumer-host', type=str, default='155.161.18.82',
                        help='Consumer host (default: 155.161.18.82)')
    parser.add_argument('--rooms', type=int, default=20,
                        help='Number of chat rooms (default: 20)')

    args = parser.parse_args()

    tester = LoadTester(
        rabbitmq_host=args.rabbitmq_host,
        consumer_host=args.consumer_host,
        num_rooms=args.rooms
    )

    tester.run_load_test(
        total_messages=args.messages,
        num_workers=args.workers
    )

if __name__ == '__main__':
    main()