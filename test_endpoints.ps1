$headers = @{ "Content-Type" = "application/json" }

Write-Host "=== 1. Health Checks ==="
$gHealth = Invoke-RestMethod -Uri "http://localhost:8080/health"
Write-Host "Gateway Health: $($gHealth.status)"
$aHealth = Invoke-RestMethod -Uri "http://localhost:8081/health"
Write-Host "Account Health: $($aHealth.status)"
Write-Host ""

Write-Host "=== 2. Submit Event 1 (250 CREDIT) ==="
$event1 = @{
    eventId = "evt-web-001"
    accountId = "acct-web-123"
    type = "CREDIT"
    amount = 250.00
    currency = "USD"
    eventTimestamp = "2026-06-27T10:00:00Z"
} | ConvertTo-Json
$res1 = Invoke-RestMethod -Uri "http://localhost:8080/events" -Method Post -Body $event1 -Headers $headers
Write-Host "Response EventId: $($res1.eventId), Status: $($res1.status)"

Write-Host "=== 3. Submit Duplicate Event 1 (Idempotency) ==="
$res2 = Invoke-RestMethod -Uri "http://localhost:8080/events" -Method Post -Body $event1 -Headers $headers
Write-Host "Response Duplicate EventId: $($res2.eventId), Status: $($res2.status) (Expect same details, 200 OK)"
Write-Host ""

Write-Host "=== 4. Submit Event 2 (100 DEBIT, Out-of-Order Timestamp) ==="
$event2 = @{
    eventId = "evt-web-002"
    accountId = "acct-web-123"
    type = "DEBIT"
    amount = 100.00
    currency = "USD"
    eventTimestamp = "2026-06-27T09:00:00Z"
} | ConvertTo-Json
$res3 = Invoke-RestMethod -Uri "http://localhost:8080/events" -Method Post -Body $event2 -Headers $headers
Write-Host "Response EventId: $($res3.eventId), Status: $($res3.status)"
Write-Host ""

Write-Host "=== 5. Verify Balance (Expect 150.00) ==="
$bal = Invoke-RestMethod -Uri "http://localhost:8080/accounts/acct-web-123/balance"
Write-Host "Balance: $($bal.balance) (Expected 150.00)"
Write-Host ""

Write-Host "=== 6. Verify Rate Limiting ==="
Write-Host "Sending 15 rapid requests (Limit is 10/sec)..."
$rateLimitViolations = 0
for ($i = 1; $i -le 15; $i++) {
    try {
        $evt = @{
            eventId = "evt-rate-$i"
            accountId = "acct-web-123"
            type = "CREDIT"
            amount = 1.00
            currency = "USD"
            eventTimestamp = "2026-06-27T11:00:00Z"
        } | ConvertTo-Json
        $null = Invoke-RestMethod -Uri "http://localhost:8080/events" -Method Post -Body $evt -Headers $headers
    } catch {
        if ($_.Exception.Response.StatusCode -eq 429) {
            $rateLimitViolations++
        }
    }
}
Write-Host "Rate Limit Violations (HTTP 429) Detected: $rateLimitViolations (Expected > 0)"
Write-Host ""

Write-Host "=== 7. Verify Async Outbox Fallback ==="
Write-Host "Stopping Account Service (Port 8081)..."
$p2 = (Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue).OwningProcess
if ($p2) { Stop-Process -Id $p2 -Force }
Start-Sleep -Seconds 1

Write-Host "Submitting new event (50 CREDIT) while Account Service is down..."
$event3 = @{
    eventId = "evt-outbox-001"
    accountId = "acct-web-123"
    type = "CREDIT"
    amount = 50.00
    currency = "USD"
    eventTimestamp = "2026-06-27T12:00:00Z"
} | ConvertTo-Json

try {
    $res4 = Invoke-RestMethod -Uri "http://localhost:8080/events" -Method Post -Body $event3 -Headers $headers
    Write-Host "Response Status: $($res4.status) (Expected: PENDING, HTTP 202 Accepted)"
} catch {
    Write-Host "Request failed: $_"
}
Write-Host ""

Write-Host "Restarting Account Service (Port 8081)..."
Start-Process -FilePath "java" -ArgumentList "-jar account-service/target/account-service-1.0.0-SNAPSHOT.jar" -WindowStyle Hidden
Write-Host "Waiting 25 seconds for Account Service to boot and Gateway Outbox scheduler to run..."
Start-Sleep -Seconds 25

Write-Host "Verifying Outbox Processing..."
try {
    $evtStatus = Invoke-RestMethod -Uri "http://localhost:8080/events/evt-outbox-001"
    Write-Host "Gateway Outbox Event status: $($evtStatus.status) (Expected: PROCESSED)"
    
    $newBal = Invoke-RestMethod -Uri "http://localhost:8080/accounts/acct-web-123/balance"
    Write-Host "Account Balance: $($newBal.balance) (Expected: 200.00)"
} catch {
    Write-Host "Outbox verification failed: $_"
}
Write-Host ""
