param(
    [switch]$Execute
)

$ErrorActionPreference = "Stop"
$targetBroker = "ssl://localhost:8884"
$backendUsername = "nexaiot-backend-lab"
$backendClientId = "nexaiot-backend-lab"
$caCertificate = Join-Path $env:LOCALAPPDATA "NexaIoT\mqtt-lab\broker-certs\ca.crt"
$credentialDirectory = Join-Path $env:LOCALAPPDATA "NexaIoT\credentials"
$credentialFile = Join-Path $credentialDirectory "backend-mqtt-password.dpapi"
$maven = "C:\Users\SHAKILA\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd"
$serverPom = Join-Path $PSScriptRoot "..\..\server\pom.xml"

if (-not $Execute) {
    throw "Pass -Execute only after explicit authorization for password rotation on localhost:8884."
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
if (Test-Path -LiteralPath $credentialFile -PathType Leaf) {
    throw "A credential file already exists. Refusing to overwrite it."
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
$backendBytes = New-Object byte[] 48
$randomGenerator.GetBytes($backendBytes)
$randomGenerator.Dispose()
$backendPlain = [Convert]::ToBase64String($backendBytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
$backendSecure = ConvertTo-SecureString $backendPlain -AsPlainText -Force
$backendEncrypted = ConvertFrom-SecureString $backendSecure
$roundTripSecure = ConvertTo-SecureString $backendEncrypted
$roundTripPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($roundTripSecure)
$roundTripPlain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($roundTripPtr)

try {
    if ($roundTripPlain -cne $backendPlain) {
        throw "DPAPI credential round-trip verification failed."
    }
    Set-Content -LiteralPath $credentialFile -Value $backendEncrypted -Encoding UTF8 -NoNewline
    & icacls.exe $credentialFile /inheritance:r /grant:r "${identity}:F" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not secure the local credential file."
    }
$permissionReport = & icacls.exe $credentialFile
if ($LASTEXITCODE -ne 0 -or ($permissionReport -match "Everyone|BUILTIN\\Users|Authenticated Users")) {
    throw "The local credential file permissions are not private."
}

    $adminPlain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($adminPtr)
    $env:MQTT_MANAGEMENT_ENABLED = "true"
    $env:MQTT_MANAGEMENT_BROKER_URL = $targetBroker
    $env:MQTT_MANAGEMENT_CLIENT_ID = "nexaiot-provisioning-management"
    $env:MQTT_MANAGEMENT_USERNAME = "admin"
    $env:MQTT_MANAGEMENT_PASSWORD = $adminPlain
    $env:MQTT_MANAGEMENT_TLS_REQUIRED = "true"
    $env:MQTT_MANAGEMENT_CA_CERTIFICATE = $caCertificate
    $env:MQTT_BACKEND_REPLACEMENT_PASSWORD = $backendPlain

    & $maven -f $serverPom `
        "-Dtest=ControlledBackendServicePasswordRotationTest" `
        "-Dnexaiot.integration.backend-rotation=true" `
        "-Dnexaiot.integration.backend-rotation-confirmation=ROTATE_BACKEND_PASSWORD_ON_LOCALHOST_8884" test
    if ($LASTEXITCODE -ne 0) {
        throw "Password rotation verification failed. The DPAPI credential file was preserved for diagnosis."
    }
}
finally {
    Remove-Item Env:MQTT_MANAGEMENT_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_BROKER_URL -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_CLIENT_ID -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_TLS_REQUIRED -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_MANAGEMENT_CA_CERTIFICATE -ErrorAction SilentlyContinue
    Remove-Item Env:MQTT_BACKEND_REPLACEMENT_PASSWORD -ErrorAction SilentlyContinue
    [Array]::Clear($backendBytes, 0, $backendBytes.Length)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($adminPtr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($roundTripPtr)
}
