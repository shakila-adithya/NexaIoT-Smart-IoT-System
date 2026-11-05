param(
    [switch]$Execute
)

$ErrorActionPreference = "Stop"
$targetBroker = "ssl://localhost:8884"
$backendUsername = "nexaiot-backend-lab"
$backendClientId = "nexaiot-backend-lab"
$caCertificate = "C:\Users\SHAKILA\AppData\Local\NexaIoT\mqtt-lab\broker-certs\ca.crt"
$credentialDirectory = Join-Path $env:LOCALAPPDATA "NexaIoT\credentials"
$credentialFile = Join-Path $credentialDirectory "backend-mqtt-password.dpapi"
$maven = "C:\Users\SHAKILA\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd"
$serverPom = Join-Path $PSScriptRoot "..\..\server\pom.xml"

if (-not $Execute) {
    throw "Pass -Execute only after explicit authorization for isolated localhost:8884 provisioning."
}
if (-not (Test-Path -LiteralPath $caCertificate -PathType Leaf)) {
    throw "The configured local CA certificate was not found."
}
if (-not (Test-Path -LiteralPath $serverPom -PathType Leaf)) {
    throw "The Spring Boot server pom.xml was not found."
}
if (Test-Path -LiteralPath $credentialFile -PathType Leaf) {
    throw "A backend credential file already exists. Refusing to overwrite it."
}

New-Item -ItemType Directory -Path $credentialDirectory -Force | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $credentialDirectory /inheritance:r /grant:r "${identity}:(OI)(CI)F" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Could not secure the local credential directory."
}

$adminSecure = Read-Host "Enter isolated Dynamic Security admin password" -AsSecureString
$adminPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminSecure)
$randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$backendBytes = New-Object byte[] 32
$randomGenerator.GetBytes($backendBytes)
$randomGenerator.Dispose()
$backendPlain = [Convert]::ToBase64String($backendBytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
$backendSecure = ConvertTo-SecureString $backendPlain -AsPlainText -Force
$backendEncrypted = ConvertFrom-SecureString $backendSecure

try {
    Set-Content -LiteralPath $credentialFile -Value $backendEncrypted -Encoding UTF8 -NoNewline
    & icacls.exe $credentialFile /inheritance:r /grant:r "${identity}:F" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not secure the local credential file."
    }

    $adminPlain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($adminPtr)
    $env:MQTT_MANAGEMENT_ENABLED = "true"
    $env:MQTT_MANAGEMENT_BROKER_URL = $targetBroker
    $env:MQTT_MANAGEMENT_CLIENT_ID = "nexaiot-provisioning-management"
    $env:MQTT_MANAGEMENT_USERNAME = "admin"
    $env:MQTT_MANAGEMENT_PASSWORD = $adminPlain
    $env:MQTT_MANAGEMENT_TLS_REQUIRED = "true"
    $env:MQTT_MANAGEMENT_CA_CERTIFICATE = $caCertificate
    $env:MQTT_BACKEND_PASSWORD = $backendPlain
    $env:MQTT_BACKEND_CLIENT_ID = $backendClientId

    & $maven -f $serverPom "-Dtest=ControlledBackendServiceProvisioningTest" "-Dnexaiot.integration.backend-provisioning=true" "-Dnexaiot.integration.backend-mutations=true" test
    if ($LASTEXITCODE -ne 0) {
        Remove-Item -LiteralPath $credentialFile -Force -ErrorAction SilentlyContinue
        throw "Backend provisioning failed. The DPAPI credential file was removed."
    }
}
catch {
    Remove-Item -LiteralPath $credentialFile -Force -ErrorAction SilentlyContinue
    throw
}
finally {
    Remove-Item Env:MQTT_MANAGEMENT_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_BACKEND_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_BACKEND_CLIENT_ID -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_BROKER_URL -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_CLIENT_ID -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_TLS_REQUIRED -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_CA_CERTIFICATE -ErrorAction SilentlyContinue
    [Array]::Clear($backendBytes, 0, $backendBytes.Length)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($adminPtr)
}
