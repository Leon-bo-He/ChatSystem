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

    print('=' * 60)
    print('CS6650 Assignment 3 - Database Performance Metrics')
    print('=' * 60)

    # Buffer pool hit ratio
    cur.execute('''
        SELECT
            sum(blks_hit) as hits,
            sum(blks_read) as reads,
            CASE WHEN sum(blks_hit) + sum(blks_read) = 0 THEN 0
            ELSE round(100.0 * sum(blks_hit) / (sum(blks_hit) + sum(blks_read)), 2)
            END as hit_ratio
        FROM pg_stat_database
    ''')
    row = cur.fetchone()
    print(f'Buffer Pool Hit Ratio: {row[2]}%')
    print(f'  - Cache Hits: {row[0]:,}')
    print(f'  - Disk Reads: {row[1]:,}')
    print()

    # Table statistics
    cur.execute('''
        SELECT
            n_tup_ins as inserts,
            n_tup_upd as updates,
            n_tup_del as deletes,
            seq_scan as seq_scans,
            idx_scan as index_scans
        FROM pg_stat_user_tables
        WHERE relname = 'messages'
    ''')
    row = cur.fetchone()
    print('Table Statistics (messages):')
    print(f'  - Inserts: {row[0]:,}')
    print(f'  - Updates: {row[1]:,}')
    print(f'  - Deletes: {row[2]:,}')
    print(f'  - Sequential Scans: {row[3]:,}')
    print(f'  - Index Scans: {row[4]:,}')
    print()

    # Lock statistics
    cur.execute('''
        SELECT count(*) FROM pg_locks WHERE granted = false
    ''')
    row = cur.fetchone()
    print(f'Lock Wait Count: {row[0]}')

    # Database size
    cur.execute('''
        SELECT pg_size_pretty(pg_database_size('chatdb'))
    ''')
    row = cur.fetchone()
    print(f'Database Size: {row[0]}')

    # Messages table size
    cur.execute('''
        SELECT pg_size_pretty(pg_total_relation_size('messages'))
    ''')
    row = cur.fetchone()
    print(f'Messages Table Size: {row[0]}')

    # Index sizes
    cur.execute('''
        SELECT indexname, pg_size_pretty(pg_relation_size(indexname::regclass)) AS size
        FROM pg_indexes
        WHERE tablename = 'messages'
        ORDER BY pg_relation_size(indexname::regclass) DESC
    ''')
    print()
    print('Index Sizes:')
    for row in cur.fetchall():
        print(f'  - {row[0]}: {row[1]}')

    print('=' * 60)
    conn.close()

if __name__ == '__main__':
    main()