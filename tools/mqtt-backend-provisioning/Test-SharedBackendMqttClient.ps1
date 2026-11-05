param(
    [switch]$Execute
)

$ErrorActionPreference = "Stop"
$targetBroker = "ssl://localhost:8884"
$backendIdentity = "nexaiot-backend-lab"
$caCertificate = Join-Path $env:LOCALAPPDATA "NexaIoT\mqtt-lab\broker-certs\ca.crt"
$credentialFile = Join-Path $env:LOCALAPPDATA "NexaIoT\credentials\backend-mqtt-password.dpapi"
$maven = "C:\Users\SHAKILA\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd"
$serverPom = Join-Path $PSScriptRoot "..\..\server\pom.xml"

if (-not $Execute) {
    throw "Pass -Execute only for the read-only localhost:8884 shared MQTT client test."
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
    $env:MQTT_BROKER_URL = $targetBroker
    $env:MQTT_CA_CERTIFICATE = $caCertificate
    $env:MQTT_USERNAME = $backendIdentity
    $env:MQTT_CLIENT_ID = $backendIdentity
    $env:MQTT_AUTH_REQUIRED = "true"
    $env:MQTT_TLS_REQUIRED = "true"
    $env:MQTT_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)

    & $maven -f $serverPom `
        "-Dtest=SharedBackendMqttClientIntegrationTest" `
        "-Dnexaiot.integration.shared-mqtt-client=true" test
}
finally {
    Remove-Item Env:MQTT_BROKER_URL -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_CA_CERTIFICATE -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_CLIENT_ID -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_AUTH_REQUIRED -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_TLS_REQUIRED -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
}
