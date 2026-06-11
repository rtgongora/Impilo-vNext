# Allow Impilo telemedicine LAN testing through Windows Firewall (run as Administrator).
# Usage: powershell -ExecutionPolicy Bypass -File scripts/dev/open-telemedicine-lan-ports.ps1

$rules = @(
    @{ Name = "Impilo UI (TCP 3000)"; Port = 3000; Protocol = "TCP" },
    @{ Name = "Impilo TURN (TCP 3478)"; Port = 3478; Protocol = "TCP" },
    @{ Name = "Impilo TURN (UDP 3478)"; Port = 3478; Protocol = "UDP" },
    @{ Name = "Impilo TURN relay (UDP 49152-49200)"; Port = "49152-49200"; Protocol = "UDP" }
)

foreach ($rule in $rules) {
    $existing = Get-NetFirewallRule -DisplayName $rule.Name -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "Rule already exists: $($rule.Name)"
        continue
    }
    New-NetFirewallRule -DisplayName $rule.Name -Direction Inbound -Action Allow `
        -Protocol $rule.Protocol -LocalPort $rule.Port | Out-Null
    Write-Host "Added: $($rule.Name)"
}

Write-Host ""
Write-Host "Share this URL with devices on the same Wi-Fi:"
$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.IPAddress -notmatch '^127\.' -and $_.PrefixOrigin -ne 'WellKnown'
} | Select-Object -First 1).IPAddress
if ($ip) {
    Write-Host "  http://${ip}:3000"
} else {
    Write-Host "  http://<your-lan-ip>:3000  (run ipconfig to find IPv4)"
}
