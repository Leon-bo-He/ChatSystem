import requests
import json

response = requests.get('http://155.161.18.82:8080/metrics')
data = response.json()

print("=" * 70)
print("METRICS API RESPONSE - CORE QUERIES")
print("=" * 70)
print()
print(f"1. Total Messages: {data['coreQueries']['totalMessages']:,}")
print(f"2. Messages Per Room: {len(data['coreQueries']['messagesPerRoom'])} rooms")
print(f"3. User Participation: {len(data['coreQueries']['userParticipation'])} entries")
print(f"4. Sample Room Messages: {len(data['coreQueries']['sampleRoomMessages'])} messages")
print(f"5. Sample User History: {len(data['coreQueries']['sampleUserHistory'])} messages")
print(f"6. Active Users (24h): {data['coreQueries']['activeUsersLast24h']} users")
print(f"7. Sample User Rooms: {len(data['coreQueries']['sampleUserRooms'])} rooms")

print()
print("=" * 70)
print("METRICS API RESPONSE - ANALYTICS QUERIES")
print("=" * 70)
print()
print(f"1. Top Active Users: {len(data['analyticsQueries']['topActiveUsers'])} users")
if len(data['analyticsQueries']['topActiveUsers']) > 0:
    top = data['analyticsQueries']['topActiveUsers'][0]
    print(f"   Top: {top['userId']} with {top['totalMessages']} messages")

print(f"2. Top Active Rooms: {len(data['analyticsQueries']['topActiveRooms'])} rooms")
if len(data['analyticsQueries']['topActiveRooms']) > 0:
    top = data['analyticsQueries']['topActiveRooms'][0]
    print(f"   Top: Room {top['roomId']} with {top['totalMessages']} messages")

print(f"3. Hourly Distribution: {len(data['analyticsQueries']['hourlyMessageDistribution'])} hours")

print()
print("=" * 70)
print("DATABASE CONNECTION POOL")
print("=" * 70)
pool = data['metadata']['databaseConnectionPool']
print(f"Active Connections: {pool['activeConnections']}")
print(f"Idle Connections: {pool['idleConnections']}")
print(f"Total Connections: {pool['totalConnections']}")
print("=" * 70)