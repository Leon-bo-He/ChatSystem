#!/usr/bin/env python3

import psycopg2

def main():
    conn = psycopg2.connect(
        host='db.ya3q6maz0zok.us-west-2.rds.amazonaws.com',
        database='chatdb',
        user='postgres',
        password='MyPassword123'
    )
    cur = conn.cursor()

    # Calculate latency percentiles
    cur.execute('''
        SELECT
            COUNT(*) as total_messages,
            MIN(EXTRACT(EPOCH FROM (created_at - timestamp)) * 1000) as min_latency,
            MAX(EXTRACT(EPOCH FROM (created_at - timestamp)) * 1000) as max_latency,
            AVG(EXTRACT(EPOCH FROM (created_at - timestamp)) * 1000) as avg_latency,
            PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (created_at - timestamp)) * 1000) as p50,
            PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (created_at - timestamp)) * 1000) as p95,
            PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (created_at - timestamp)) * 1000) as p99
        FROM messages
    ''')

    row = cur.fetchone()

    print('=' * 60)
    print('CS6650 Assignment 3 - Write Latency Analysis')
    print('=' * 60)
    print(f'Total Messages Analyzed: {row[0]:,}')
    print()
    print('Write Latency (message timestamp to database commit):')
    print('-' * 60)
    print(f'  Min:     {row[1]:,.2f} ms')
    print(f'  Max:     {row[2]:,.2f} ms')
    print(f'  Average: {row[3]:,.2f} ms')
    print()
    print('Latency Percentiles:')
    print(f'  p50 (median): {row[4]:,.2f} ms')
    print(f'  p95:          {row[5]:,.2f} ms')
    print(f'  p99:          {row[6]:,.2f} ms')
    print('=' * 60)
    print()
    print('Note: High latency includes queue wait time during peak load.')
    print('      Batch processing: 1000 messages / 500ms flush interval.')

    conn.close()

if __name__ == '__main__':
    main()