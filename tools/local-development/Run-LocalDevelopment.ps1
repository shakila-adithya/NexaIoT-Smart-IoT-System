[CmdletBinding(SupportsShouldProcess)]
param(
    [ValidateSet('Start', 'StartBackend', 'StopBackend', 'RestartBackend', 'Stop', 'Status', 'Initialize', 'Clean', 'Help')]
    [string]$Action = 'Start',
    [switch]$Initialize
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$compose = Join-Path $root 'docker-compose.yml'
$runtime = Join-Path $root '.runtime'
$envFile = Join-Path $runtime 'compose.env'
$stateFile = Join-Path $runtime 'state.json'
$project = 'nexaiot-local-dev'
$javaProcess = $null
$stdoutTask = $null
$stderrTask = $null

function Require-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) { throw "$name is required." }
}

function Get-Python {
    $candidate = Join-Path $repo 'simulator\.venv\bin\python.exe'
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        $candidate = Join-Path $repo 'simulator\.venv\Scripts\python.exe'
    }
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { throw 'The project-local simulator Python environment is required.' }
    return $candidate
}

function New-RandomBase64([int]$length = 32) {
    $bytes = New-Object byte[] $length
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

function Test-PortFree([int]$port) {
    $connection = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($connection) { throw "Required local development port $port is already in use." }
}

function New-Certificates {
    Require-Command openssl
    $certs = Join-Path $runtime 'certs'
    New-Item -ItemType Directory -Force -Path $certs | Out-Null
    $config = Join-Path $runtime 'openssl.cnf'
    @'
[req]
distinguished_name = req_distinguished_name
prompt = no
[req_distinguished_name]
CN = nexaiot-local-dev
[v3_ca]
basicConstraints = critical,CA:TRUE
keyUsage = critical,keyCertSign,cRLSign
[v3_server]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names
[alt_names]
DNS.1 = localhost
IP.1 = 127.0.0.1
'@ | Set-Content -LiteralPath $config -NoNewline
    & openssl genrsa -out (Join-Path $certs 'ca.key') 3072 | Out-Null
    & openssl req -x509 -new -nodes -key (Join-Path $certs 'ca.key') -sha256 -days 365 -out (Join-Path $certs 'ca.crt') -config $config -extensions v3_ca | Out-Null
    & openssl genrsa -out (Join-Path $certs 'server.key') 2048 | Out-Null
    & openssl req -new -key (Join-Path $certs 'server.key') -out (Join-Path $runtime 'server.csr') -config $config | Out-Null
    & openssl x509 -req -in (Join-Path $runtime 'server.csr') -CA (Join-Path $certs 'ca.crt') -CAkey (Join-Path $certs 'ca.key') -CAcreateserial -out (Join-Path $certs 'server.crt') -days 365 -sha256 -extfile $config -extensions v3_server | Out-Null
    & openssl genrsa -out (Join-Path $certs 'app.key') 2048 | Out-Null
    & openssl req -new -key (Join-Path $certs 'app.key') -out (Join-Path $runtime 'app.csr') -config $config | Out-Null
    & openssl x509 -req -in (Join-Path $runtime 'app.csr') -CA (Join-Path $certs 'ca.crt') -CAkey (Join-Path $certs 'ca.key') -CAcreateserial -out (Join-Path $certs 'app.crt') -days 365 -sha256 -extfile $config -extensions v3_server | Out-Null
    $password = (Get-LocalValues).NEXAIOT_LOCAL_KEYSTORE_PASSWORD
    & openssl pkcs12 -export -out (Join-Path $certs 'app.p12') -inkey (Join-Path $certs 'app.key') -in (Join-Path $certs 'app.crt') -certfile (Join-Path $certs 'ca.crt') -passout ("pass:" + $password) -name server | Out-Null
}

function New-EnvironmentFile {
    $required = @(
        'NEXAIOT_TEST_ADMIN_USERNAME', 'NEXAIOT_TEST_ADMIN_PASSWORD',
        'NEXAIOT_TEST_BACKEND_USERNAME', 'NEXAIOT_TEST_BACKEND_CLIENT_ID',
        'NEXAIOT_TEST_BACKEND_PASSWORD', 'NEXAIOT_TEST_MANAGEMENT_USERNAME',
        'NEXAIOT_TEST_MANAGEMENT_CLIENT_ID', 'NEXAIOT_TEST_MANAGEMENT_PASSWORD',
        'NEXAIOT_LOCAL_KEYSTORE_PASSWORD', 'NEXAIOT_CREDENTIAL_ESCROW_KEY',
        'NEXAIOT_B46_JWT_SECRET'
    )
    if (Test-Path -LiteralPath $envFile -PathType Leaf) {
        $keys = @(Get-Content -LiteralPath $envFile | ForEach-Object {
            if ($_ -match '^([^=]+)=') { $matches[1] }
        })
        if (@($required | Where-Object { $_ -notin $keys }).Count -eq 0) { return }
    }
    New-Item -ItemType Directory -Force -Path $runtime | Out-Null
    $values = [ordered]@{
        NEXAIOT_TEST_ADMIN_USERNAME = 'nexaiot-local-admin'
        NEXAIOT_TEST_ADMIN_PASSWORD = New-RandomBase64
        NEXAIOT_TEST_BACKEND_USERNAME = 'nexaiot-backend-lab'
        NEXAIOT_TEST_BACKEND_CLIENT_ID = 'nexaiot-backend-lab'
        NEXAIOT_TEST_BACKEND_PASSWORD = New-RandomBase64
        NEXAIOT_TEST_MANAGEMENT_USERNAME = 'nexaiot-local-management'
        NEXAIOT_TEST_MANAGEMENT_CLIENT_ID = 'nexaiot-local-management'
        NEXAIOT_TEST_MANAGEMENT_PASSWORD = New-RandomBase64
        NEXAIOT_LOCAL_KEYSTORE_PASSWORD = New-RandomBase64
        NEXAIOT_CREDENTIAL_ESCROW_KEY = New-RandomBase64
        NEXAIOT_B46_JWT_SECRET = New-RandomBase64
    }
    $lines = foreach ($entry in $values.GetEnumerator()) { "$($entry.Key)=$($entry.Value)" }
    [IO.File]::WriteAllLines($envFile, [string[]]$lines, [Text.UTF8Encoding]::new($false))
}

function Get-LocalValues {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^([^=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
    }
    return $values
}

function Test-KeyStore {
    $values = Get-LocalValues
    $keystore = Join-Path $runtime 'certs\app.p12'
    if (-not (Test-Path -LiteralPath $keystore -PathType Leaf)) { return $false }
    & keytool -list -keystore $keystore -storepass $values.NEXAIOT_LOCAL_KEYSTORE_PASSWORD 2>$null | Out-Null
    return $LASTEXITCODE -eq 0
}

function Invoke-Compose([string[]]$arguments) {
    & docker compose --env-file $envFile -p $project -f $compose @arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed with exit code $LASTEXITCODE." }
}

function Wait-Healthy([string]$service, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        $id = (docker compose --env-file $envFile -p $project -f $compose ps -q $service 2>$null)
        if ($id) {
            $health = docker inspect --format '{{.State.Health.Status}}' $id 2>$null
            if (($health -join '').Trim() -eq 'healthy') { return }
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "$service did not become healthy."
}

function ConvertTo-Argument([string]$value) {
    if ($value -notmatch '[\s"]') { return $value }
    return '"' + $value.Replace('"', '\"') + '"'
}

function Start-Application {
    $jar = Join-Path $repo 'server\target\server-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Spring Boot JAR is missing: $jar" }
    $values = Get-LocalValues
    $java = (Get-Command java -ErrorAction Stop).Source
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $java
    $psi.WorkingDirectory = $repo
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.Arguments = '-jar {0} --spring.profiles.active=b46-disposable' -f (ConvertTo-Argument $jar)
    $settings = @{
        NEXAIOT_B46_APPLICATION_MODE = 'host'
        NEXAIOT_B46_EXPECTED_MONGO_HOST = '127.0.0.1'
        NEXAIOT_B46_EXPECTED_MONGO_PORT = '27018'
        NEXAIOT_B46_EXPECTED_MQTT_HOST = '127.0.0.1'
        NEXAIOT_B46_EXPECTED_MQTT_PORT = '8885'
        MONGODB_URI = 'mongodb://127.0.0.1:27018/nexaiot_b46'
        MQTT_BROKER_URL = 'ssl://127.0.0.1:8885'
        MQTT_CLIENT_ID = $values.NEXAIOT_TEST_BACKEND_CLIENT_ID
        MQTT_USERNAME = $values.NEXAIOT_TEST_BACKEND_USERNAME
        MQTT_PASSWORD = $values.NEXAIOT_TEST_BACKEND_PASSWORD
        MQTT_CA_CERTIFICATE = (Join-Path $runtime 'certs\ca.crt')
        MQTT_MANAGEMENT_ENABLED = 'true'
        MQTT_MANAGEMENT_BROKER_URL = 'ssl://127.0.0.1:8885'
        MQTT_MANAGEMENT_CLIENT_ID = $values.NEXAIOT_TEST_MANAGEMENT_CLIENT_ID
        MQTT_MANAGEMENT_USERNAME = $values.NEXAIOT_TEST_MANAGEMENT_USERNAME
        MQTT_MANAGEMENT_PASSWORD = $values.NEXAIOT_TEST_MANAGEMENT_PASSWORD
        MQTT_MANAGEMENT_TLS_REQUIRED = 'true'
        MQTT_MANAGEMENT_CA_CERTIFICATE = (Join-Path $runtime 'certs\ca.crt')
        NEXAIOT_CREDENTIAL_ESCROW_KEY = $values.NEXAIOT_CREDENTIAL_ESCROW_KEY
        NEXAIOT_CREDENTIAL_ESCROW_KEY_VERSION = 'local-dev-v1'
        PAIRING_SESSION_EXPIRY_SECONDS = '600'
        CREDENTIAL_DELIVERY_CHALLENGE_LIFETIME_SECONDS = '120'
        ACTIVATION_CHALLENGE_LIFETIME_SECONDS = '120'
        JWT_SECRET = $values.NEXAIOT_B46_JWT_SECRET
        CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:4173,http://localhost:4173,http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:5174,http://localhost:5174'
        SERVER_SSL_KEY_STORE = (Join-Path $runtime 'certs\app.p12')
        SERVER_SSL_KEY_STORE_PASSWORD = $values.NEXAIOT_LOCAL_KEYSTORE_PASSWORD
        SERVER_PORT = '8443'
        SERVER_ADDRESS = '127.0.0.1'
    }
    foreach ($entry in $settings.GetEnumerator()) { $psi.EnvironmentVariables[$entry.Key] = [string]$entry.Value }
    $script:javaProcess = [Diagnostics.Process]::new()
    $javaProcess.StartInfo = $psi
    if (-not $javaProcess.Start()) { throw 'Local Spring Boot process did not start.' }
    $script:stdoutTask = $javaProcess.StandardOutput.ReadToEndAsync()
    $script:stderrTask = $javaProcess.StandardError.ReadToEndAsync()
    Set-Content -LiteralPath $stateFile -Value (@{ Pid = $javaProcess.Id; Java = $java; Jar = $jar } | ConvertTo-Json) -Encoding UTF8 -NoNewline
}

function Save-ApplicationLogs {
    if (-not $stdoutTask -and -not $stderrTask) { return }
    $values = Get-LocalValues
    $redactions = @($values.Values | Where-Object { $_ })
    $stdout = if ($stdoutTask) { $stdoutTask.Result } else { '' }
    $stderr = if ($stderrTask) { $stderrTask.Result } else { '' }
    foreach ($secret in $redactions) {
        $stdout = $stdout.Replace($secret, '[REDACTED]')
        $stderr = $stderr.Replace($secret, '[REDACTED]')
    }
    [IO.File]::WriteAllText((Join-Path $runtime 'spring-boot.stdout.log'), $stdout)
    [IO.File]::WriteAllText((Join-Path $runtime 'spring-boot.stderr.log'), $stderr)
}

function Assert-ApplicationAlive {
    if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) { throw 'Local backend state is missing.' }
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $process = Get-Process -Id ([int]$state.Pid) -ErrorAction SilentlyContinue
    if (-not $process) { throw 'Local backend process is not running.' }
    $cim = Get-CimInstance Win32_Process -Filter "ProcessId=$($state.Pid)" -ErrorAction SilentlyContinue
    if (-not $cim -or $cim.ExecutablePath -ne $state.Java -or $cim.CommandLine -notmatch [regex]::Escape($state.Jar) -or $cim.CommandLine -notmatch 'b46-disposable') {
        throw 'The recorded process is not the local backend process; refusing to terminate it.'
    }
    return $process
}

function Stop-Application {
    if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) { return }
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $process = Get-Process -Id ([int]$state.Pid) -ErrorAction SilentlyContinue
    if ($process) { $process = Assert-ApplicationAlive }
    if ($process -and -not $process.HasExited) { $process.Kill(); [void]$process.WaitForExit(10000) }
    Remove-Item -LiteralPath $stateFile -Force
}

function Test-Health {
    $curl = (Get-Command curl.exe -ErrorAction Stop).Source
    $ca = Join-Path $runtime 'certs\ca.crt'
    $output = & $curl --fail --silent --show-error --connect-timeout 2 --max-time 5 --cacert $ca https://127.0.0.1:8443/api/health 2>&1
    if ($LASTEXITCODE -ne 0 -or ($output -join '') -notmatch 'NexaIoT backend is running') { throw 'Local HTTPS health verification failed.' }
}

function Initialize-Owner {
    $python = Get-Python
    & $python (Join-Path $root 'setup_owner.py') 'https://127.0.0.1:8443' (Join-Path $runtime 'certs\ca.crt')
    if ($LASTEXITCODE -ne 0) { throw 'Local owner/device setup failed.' }
}

function Start-Local {
    Require-Command docker
    Require-Command openssl
    Require-Command java
    Test-PortFree 27018; Test-PortFree 8885; Test-PortFree 8443
    New-Item -ItemType Directory -Force -Path $runtime | Out-Null
    New-EnvironmentFile
    Require-Command keytool
    if (-not (Test-KeyStore)) {
        $certs = Join-Path $runtime 'certs'
        if (Test-Path -LiteralPath $certs) { Remove-Item -LiteralPath $certs -Recurse -Force }
        New-Certificates
    }
    Invoke-Compose @('up', '-d', 'mongo', 'mosquitto')
    Wait-Healthy 'mongo' 60
    Wait-Healthy 'mosquitto' 60
    try {
        Start-Application
        $deadline = (Get-Date).AddSeconds(180)
        do {
            $process = Get-Process -Id $javaProcess.Id -ErrorAction SilentlyContinue
            if (-not $process) { throw 'Local Spring Boot exited during startup.' }
            try { Test-Health; break } catch { Start-Sleep -Seconds 2 }
        } while ((Get-Date) -lt $deadline)
        Test-Health
    } catch {
        Save-ApplicationLogs
        throw
    }
    Write-Output 'LOCAL_BACKEND_READY: https://127.0.0.1:8443'
    Write-Output 'LOCAL_MQTT_READY: ssl://127.0.0.1:8885'
    Write-Output ("LOCAL_CA: " + (Join-Path $runtime 'certs\ca.crt'))
    if ($Initialize) { Initialize-Owner }
}

function Restart-Backend {
    Require-Command java
    Assert-ApplicationAlive | Out-Null
    Stop-Application
    try {
        Start-Application
        $deadline = (Get-Date).AddSeconds(180)
        do {
            $process = Get-Process -Id $javaProcess.Id -ErrorAction SilentlyContinue
            if (-not $process) { throw 'Local Spring Boot exited during restart.' }
            try { Test-Health; break } catch { Start-Sleep -Seconds 2 }
        } while ((Get-Date) -lt $deadline)
        Test-Health
    } catch {
        Save-ApplicationLogs
        throw
    }
    Write-Output 'LOCAL_BACKEND_READY: https://127.0.0.1:8443'
}

function Start-BackendOnly {
    Require-Command java
    if (Test-Path -LiteralPath $stateFile) {
        $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        $process = Get-Process -Id ([int]$state.Pid) -ErrorAction SilentlyContinue
        if ($process) { throw 'Local backend process is already running.' }
    }
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) { throw 'Local development environment is not initialized.' }
    if (-not (Test-KeyStore)) { throw 'Local HTTPS certificate keystore is unavailable.' }
    try {
        Start-Application
        $deadline = (Get-Date).AddSeconds(180)
        do {
            $process = Get-Process -Id $javaProcess.Id -ErrorAction SilentlyContinue
            if (-not $process) { throw 'Local Spring Boot exited during startup.' }
            try { Test-Health; break } catch { Start-Sleep -Seconds 2 }
        } while ((Get-Date) -lt $deadline)
        Test-Health
    } catch {
        Save-ApplicationLogs
        throw
    }
    Write-Output 'LOCAL_BACKEND_READY: https://127.0.0.1:8443'
}

function Show-Status {
    if (Test-Path -LiteralPath $envFile -PathType Leaf) {
        docker compose --env-file $envFile -p $project -f $compose ps
    } else { Write-Output 'LOCAL_DEVELOPMENT_NOT_INITIALIZED' }
    if (Test-Path -LiteralPath $stateFile -PathType Leaf) {
        $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        $process = Get-Process -Id ([int]$state.Pid) -ErrorAction SilentlyContinue
        Write-Output ("LOCAL_BACKEND_PROCESS: " + $(if ($process) { 'running' } else { 'stopped' }))
    }
}

function Show-Help {
    Write-Output 'Start:      Run-LocalDevelopment.ps1 -Action Start [-Initialize]'
    Write-Output 'StartBackend: Run-LocalDevelopment.ps1 -Action StartBackend'
    Write-Output 'Status:     Run-LocalDevelopment.ps1 -Action Status'
    Write-Output 'Initialize: Run-LocalDevelopment.ps1 -Action Initialize'
    Write-Output 'Stop:       Run-LocalDevelopment.ps1 -Action Stop'
    Write-Output 'Clean:      Run-LocalDevelopment.ps1 -Action Clean'
    Write-Output 'Frontend:   set VITE_API_URL=https://127.0.0.1:8443 and run npm.cmd run dev -- --host 127.0.0.1 --port 5174'
}

if ($Action -eq 'Help') { Show-Help; exit 0 }
if ($Action -eq 'Status') { Show-Status; exit 0 }
if (-not $PSCmdlet.ShouldProcess($project, "$Action local development resources")) { exit 0 }
if ($Action -eq 'Initialize') { Test-Health; Initialize-Owner; exit 0 }
if ($Action -eq 'Start') { Start-Local; exit 0 }
if ($Action -eq 'StartBackend') { Start-BackendOnly; exit 0 }
if ($Action -eq 'StopBackend') { Stop-Application; exit 0 }
if ($Action -eq 'RestartBackend') { Restart-Backend; exit 0 }
if ($Action -eq 'Stop') { Stop-Application; if (Test-Path -LiteralPath $envFile) { Invoke-Compose @('down', '--remove-orphans') }; exit 0 }
if ($Action -eq 'Clean') {
    Stop-Application
    if (Test-Path -LiteralPath $envFile) { Invoke-Compose @('down', '-v', '--remove-orphans') }
    if (Test-Path -LiteralPath $runtime) { Remove-Item -LiteralPath $runtime -Recurse -Force }
}
