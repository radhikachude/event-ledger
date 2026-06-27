$headers = @{ "Content-Type" = "application/json" }

Write-Host "--- 1. Health Checks ---"
try {
    $gHealth = Invoke-RestMethod -Uri "http://localhost:8080/health"
    Write-Host "Gateway Health: $($gHealth | ConvertTo-Json -Depth 2)"
} catch {
    Write-Host "Gateway Health failed: $_"
}

try {
    $aHealth = Invoke-RestMethod -Uri "http://localhost:8081/health"
    Write-Host "Account Health: $($aHealth | ConvertTo-Json -Depth 2)"
} catch {
    Write-Host "Account Health failed: $_"
}
Write-Host ""

$event1 = @{
    eventId = "evt-web-001"
    accountId = "acct-web-123"
    type = "CREDIT"
    amount = 250.00
    currency = "USD"
    eventTimestamp = "2026-06-27T10:00:00Z"
} | ConvertTo-Json

Write-Host "--- 2. Submit Event 1 (250 CREDIT) ---"
try {
    $res1 = Invoke-RestMethod -Uri "http://localhost:8080/events" -Method Post -Body $event1 -Headers $headers
    Write-Host "Response: $($res1 | ConvertTo-Json)"
} catch {
    Write-Host "Submit Event 1 failed: $_"
}
Write-Host ""

Write-Host "--- 3. Submit Duplicate Event 1 (Idempotency) ---"
try {
    $res2 = Invoke-RestMethod -Uri "http://localhost:8080/events" -Method Post -Body $event1 -Headers $headers
    Write-Host "Response: $($res2 | ConvertTo-Json)"
} catch {
    Write-Host "Submit Duplicate Event 1 failed: $_"
}
Write-Host ""

$event2 = @{
    eventId = "evt-web-002"
    accountId = "acct-web-123"
    type = "DEBIT"
    amount = 100.00
    currency = "USD"
    eventTimestamp = "2026-06-27T09:00:00Z"
} | ConvertTo-Json

Write-Host "--- 4. Submit Event 2 (100 DEBIT, Out-of-Order) ---"
try {
    $res3 = Invoke-RestMethod -Uri "http://localhost:8080/events" -Method Post -Body $event2 -Headers $headers
    Write-Host "Response: $($res3 | ConvertTo-Json)"
} catch {
    Write-Host "Submit Event 2 failed: $_"
}
Write-Host ""

Write-Host "--- 5. Get Balance (Expect 250 - 100 = 150) ---"
try {
    $bal = Invoke-RestMethod -Uri "http://localhost:8080/accounts/acct-web-123/balance"
    Write-Host "Response: $($bal | ConvertTo-Json)"
} catch {
    Write-Host "Get Balance failed: $_"
}
Write-Host ""

Write-Host "--- 6. Get Details (Expect 2 transactions sorted chronologically) ---"
try {
    $det = Invoke-RestMethod -Uri "http://localhost:8080/accounts/acct-web-123"
    Write-Host "Response: $($det | ConvertTo-Json -Depth 5)"
} catch {
    Write-Host "Get Details failed: $_"
}
Write-Host ""

Write-Host "--- 7. List Events (Expect sorted: evt-web-002 at 09:00 first) ---"
try {
    $evts = Invoke-RestMethod -Uri "http://localhost:8080/events?account=acct-web-123"
    Write-Host "Response: $($evts | ConvertTo-Json)"
} catch {
    Write-Host "List Events failed: $_"
}
Write-Host ""
