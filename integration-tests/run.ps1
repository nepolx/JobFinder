$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$compose = @('-p', 'vacancybot-it', '-f', (Join-Path $root 'docker-compose.yml'), '-f', (Join-Path $root 'docker-compose.integration.yml'))
$userId = 987654321
$script:updateId = 7000
$previousToken = $env:TELEGRAM_BOT_TOKEN
$env:TELEGRAM_BOT_TOKEN = 'integration-test-token'

function Wait-Http($Url, $TimeoutSeconds = 120) {
    $until = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try { return Invoke-RestMethod -Uri $Url -TimeoutSec 3 } catch { Start-Sleep -Seconds 2 }
    } while ((Get-Date) -lt $until)
    throw "Timed out waiting for $Url"
}

function Send-Message($Text) {
    $script:updateId++
    $body = @{ update_id = $script:updateId; message = @{ message_id = $script:updateId; from = @{ id = $userId; is_bot = $false; first_name = 'IT' }; chat = @{ id = $userId; type = 'private' }; text = $Text } } | ConvertTo-Json -Depth 8
    Invoke-RestMethod -Method Post -Uri 'http://localhost:18080/webhook' -ContentType 'application/json' -Body $body | Out-Null
}

function Press-Button($Data) {
    $messages = Invoke-RestMethod -Uri 'http://localhost:8090/sent'
    $last = $messages.messages[-1]
    $script:updateId++
    $body = @{ update_id = $script:updateId; callback_query = @{ id = "it-$script:updateId"; from = @{ id = $userId; is_bot = $false; first_name = 'IT' }; message = @{ message_id = $last.message_id; chat = @{ id = $userId; type = 'private' }; text = $last.text }; data = $Data } } | ConvertTo-Json -Depth 12
    try { Invoke-RestMethod -Method Post -Uri 'http://localhost:18080/webhook' -ContentType 'application/json' -Body $body | Out-Null }
    catch { Write-Host "Rejected callback payload: $body"; docker compose @compose logs --no-color --tail 80 bot-service | Select-String 'WARN|ERROR|HttpMessage|Exception|JSON'; throw }
}

try {
    if ($env:INTEGRATION_SKIP_BUILD -eq '1') { docker compose @compose up -d }
    else { docker compose @compose up -d --build }
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed to start the integration stack.' }
    Wait-Http 'http://localhost:18081/actuator/health' | Out-Null
    Wait-Http 'http://localhost:18080/actuator/health' | Out-Null
    Wait-Http 'http://localhost:18082/actuator/health' | Out-Null

    Send-Message '/start'
    Send-Message '/subscribe'
    Send-Message 'Integration Engineer'
    foreach ($step in 1..6) { Press-Button 'sub_skip' }
    Press-Button 'sub_confirm'
    $saved = Invoke-RestMethod -Uri "http://localhost:18081/api/users/$userId/subscriptions?onlyActive=true"
    if (@($saved).Count -ne 1) { $debug = Invoke-RestMethod -Uri 'http://localhost:8090/sent'; Write-Host (ConvertTo-Json $debug.messages -Depth 10); throw 'Bot wizard did not create an active subscription in db-service.' }
    Invoke-RestMethod -Method Post -Uri 'http://localhost:8090/enable' -ContentType 'application/json' -Body '{}' | Out-Null

    $deadline = (Get-Date).AddMinutes(2)
    do {
        Start-Sleep -Seconds 2
        $sent = Invoke-RestMethod -Uri 'http://localhost:8090/sent'
        $match = @($sent.messages | Where-Object { $_.chat_id -eq $userId -and $_.text -match 'Integration Engineer' })
    } while ($match.Count -eq 0 -and (Get-Date) -lt $deadline)
    if ($match.Count -eq 0) { throw 'Notifier did not send the worker event to the subscribed user.' }

    Write-Host 'PASS: bot -> db-service -> worker -> db/outbox -> RabbitMQ -> notifier -> Telegram fixture'
} catch {
    docker compose @compose logs --no-color --tail 40 bot-service worker-service notifier-service | Select-String 'WARN|ERROR|HttpMessage|Exception|JSON|subscriber|change'
    throw
} finally {
    docker compose @compose down -v
    if ($LASTEXITCODE -ne 0) { Write-Warning 'Could not fully remove the integration stack.' }
    $env:TELEGRAM_BOT_TOKEN = $previousToken
}
