import urllib.request
import json
import time

def make_request(url, method="GET", data=None):
    req = urllib.request.Request(url, method=method)
    req.add_header("Content-Type", "application/json")
    
    body = None
    if data:
        body = json.dumps(data).encode("utf-8")
        
    try:
        with urllib.request.urlopen(req, data=body) as response:
            return response.status, json.loads(response.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        try:
            err_body = json.loads(e.read().decode("utf-8") or "{}")
        except Exception:
            err_body = e.reason
        return e.code, err_body
    except Exception as e:
        return 500, str(e)

print("--- 1. Health Checks ---")
print("Gateway Health:", make_request("http://localhost:8080/health"))
print("Account Health:", make_request("http://localhost:8081/health"))
print()

event1 = {
    "eventId": "evt-web-001",
    "accountId": "acct-web-123",
    "type": "CREDIT",
    "amount": 250.00,
    "currency": "USD",
    "eventTimestamp": "2026-06-27T10:00:00Z"
}

print("--- 2. Submit Event 1 (250 CREDIT) ---")
status, resp = make_request("http://localhost:8080/events", "POST", event1)
print(f"Status: {status}, Response: {resp}")
print()

print("--- 3. Submit Duplicate Event 1 (Idempotency check) ---")
status, resp = make_request("http://localhost:8080/events", "POST", event1)
print(f"Status: {status}, Response: {resp}")
print()

event2 = {
    "eventId": "evt-web-002",
    "accountId": "acct-web-123",
    "type": "DEBIT",
    "amount": 100.00,
    "currency": "USD",
    "eventTimestamp": "2026-06-27T09:00:00Z" # Out of chronological order
}

print("--- 4. Submit Event 2 (100 DEBIT, Out-of-Order Timestamp) ---")
status, resp = make_request("http://localhost:8080/events", "POST", event2)
print(f"Status: {status}, Response: {resp}")
print()

print("--- 5. Get Balance (Should be 250 - 100 = 150) ---")
status, resp = make_request("http://localhost:8080/accounts/acct-web-123/balance")
print(f"Status: {status}, Response: {resp}")
print()

print("--- 6. Get Account Details ---")
status, resp = make_request("http://localhost:8080/accounts/acct-web-123")
print(f"Status: {status}, Response: {resp}")
print()

print("--- 7. List Events (Should be sorted: evt-web-002 at 09:00 first, then evt-web-001 at 10:00) ---")
status, resp = make_request("http://localhost:8080/events?account=acct-web-123")
print(f"Status: {status}")
for event in resp:
    print(f"  Event: {event.get('eventId')} at {event.get('eventTimestamp')}, type: {event.get('type')}, amount: {event.get('amount')}")
print()
