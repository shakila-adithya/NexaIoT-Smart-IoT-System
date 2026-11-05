param(
    [switch]$Execute
)

$ErrorActionPreference = "Stop"
$targetBroker = "ssl://localhost:8884"
$caCertificate = Join-Path $env:LOCALAPPDATA "NexaIoT\mqtt-lab\broker-certs\ca.crt"
$credentialFile = Join-Path $env:LOCALAPPDATA "NexaIoT\credentials\backend-mqtt-password.dpapi"
$maven = "C:\Users\SHAKILA\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd"
$serverPom = Join-Path $PSScriptRoot "..\..\server\pom.xml"

if (-not $Execute) {
    throw "Pass -Execute only for the read-only localhost:8884 credential test."
}
if (-not (Test-Path -LiteralPath $credentialFile -PathType Leaf)) {
    throw "The DPAPI credential file was not found."
}
if (-not (Test-Path -LiteralPath $caCertificate -PathType Leaf)) {
    throw "The configured local CA certificate was not found."
}
if (-not (Test-Path -LiteralPath $serverPom -PathType Leaf)) {
    throw "The Spring Boot server pom.xml was not found."
}
if (-not (Test-Path -LiteralPath $maven -PathType Leaf)) {
    throw "The configured Maven executable was not found."
}

$encrypted = Get-Content -LiteralPath $credentialFile -Raw
$securePassword = ConvertTo-SecureString $encrypted
$passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $env:MQTT_BACKEND_BROKER_URL = $targetBroker
    $env:MQTT_BACKEND_CA_CERTIFICATE = $caCertificate
    $env:MQTT_BACKEND_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)

    & $maven -f $serverPom `
        "-Dtest=StoredBackendCredentialAuthenticationTest" `
        "-Dnexaiot.integration.backend-credential-auth=true" test
}
finally {
    Remove-Item Env:MQTT_BACKEND_BROKER_URL -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_BACKEND_CA_CERTIFICATE -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_BACKEND_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
}
