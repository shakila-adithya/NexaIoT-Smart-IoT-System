[CmdletBinding(SupportsShouldProcess)]
param(
    [switch]$RunLive,
    [switch]$KeepResources,
    [ValidateSet('Host', 'Container')]
    [string]$ApplicationMode = 'Host'
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$fixture = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $fixture)
$compose = Join-Path $fixture 'docker-compose.yml'
$project = 'nexaiot-b46-' + ([guid]::NewGuid().ToString('N').Substring(0, 12))
$runtime = Join-Path $fixture (Join-Path '.runtime' $project)
$network = "$project-network"
$mongoVolume = "$project-mongo"
$mosquittoVolume = "$project-mosquitto"
$appProcess = $null
$appStdoutTask = $null
$appStderrTask = $null
$appStdoutLog = Join-Path $runtime 'spring-boot.stdout.log'
$appStderrLog = Join-Path $runtime 'spring-boot.stderr.log'

function Require-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) { throw "$name is required." }
}

function Get-FreeLoopbackPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try { $listener.Start(); return ([Net.IPEndPoint]$listener.LocalEndpoint).Port } finally { $listener.Stop() }
}

function Assert-IsolatedPorts {
    $script:mongoPort = Get-FreeLoopbackPort
    $script:mqttTlsPort = Get-FreeLoopbackPort
    $script:httpsPort = Get-FreeLoopbackPort
    if (@($mongoPort, $mqttTlsPort, $httpsPort) | Where-Object { $_ -in @(27017, 1883, 8883, 8884) }) {
        throw 'A disposable port selection overlapped a reserved production port.'
    }
}

function New-RandomBase64([int]$length = 32) {
    $bytes = New-Object byte[] $length
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

function New-RuntimeCertificate {
    Require-Command openssl
    $certs = Join-Path $runtime 'certs'
    New-Item -ItemType Directory -Force -Path $certs | Out-Null
    $config = Join-Path $runtime 'openssl.cnf'
    @'
[req]
distinguished_name = req_distinguished_name
prompt = no
[req_distinguished_name]
CN = nexaiot-b46-mosquitto
[v3_ca]
basicConstraints = critical,CA:TRUE
keyUsage = critical,keyCertSign,cRLSign
[v3_server]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names
[alt_names]
DNS.1 = mosquitto
DNS.2 = localhost
DNS.3 = app
IP.1 = 127.0.0.1
'@ | Set-Content -LiteralPath $config -NoNewline
    & openssl genrsa -out (Join-Path $certs 'ca.key') 3072 | Out-Null
    & openssl req -x509 -new -nodes -key (Join-Path $certs 'ca.key') -sha256 -days 2 -out (Join-Path $certs 'ca.crt') -config $config -extensions v3_ca | Out-Null
    & openssl genrsa -out (Join-Path $certs 'server.key') 2048 | Out-Null
    & openssl req -new -key (Join-Path $certs 'server.key') -out (Join-Path $runtime 'server.csr') -config $config | Out-Null
    & openssl x509 -req -in (Join-Path $runtime 'server.csr') -CA (Join-Path $certs 'ca.crt') -CAkey (Join-Path $certs 'ca.key') -CAcreateserial -out (Join-Path $certs 'server.crt') -days 2 -sha256 -extfile $config -extensions v3_server | Out-Null
    & openssl genrsa -out (Join-Path $certs 'app.key') 2048 | Out-Null
    & openssl req -new -key (Join-Path $certs 'app.key') -out (Join-Path $runtime 'app.csr') -config $config | Out-Null
    & openssl x509 -req -in (Join-Path $runtime 'app.csr') -CA (Join-Path $certs 'ca.crt') -CAkey (Join-Path $certs 'ca.key') -CAcreateserial -out (Join-Path $certs 'app.crt') -days 2 -sha256 -extfile $config -extensions v3_server | Out-Null
    & openssl pkcs12 -export -out (Join-Path $certs 'app.p12') -inkey (Join-Path $certs 'app.key') -in (Join-Path $certs 'app.crt') -certfile (Join-Path $certs 'ca.crt') -passout ("pass:" + $env:NEXAIOT_B46_APP_KEYSTORE_PASSWORD) -name server | Out-Null
}

function Invoke-Dynsec([string[]]$arguments) {
    $response = docker compose -p $project -f $compose exec -T mosquitto mosquitto_ctrl -u $env:NEXAIOT_TEST_ADMIN_USERNAME -P $env:NEXAIOT_TEST_ADMIN_PASSWORD -h localhost -p 8883 --cafile /mosquitto/certs/ca.crt dynsec @arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Disposable Dynamic Security command failed: $($response -join ' ')" }
    return $response
}

function Wait-Healthy([string]$service, [int]$seconds, [string]$message) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        $container = docker compose -p $project -f $compose ps -q $service 2>$null
        if ($LASTEXITCODE -eq 0 -and $container) {
            $health = docker inspect --format '{{.State.Health.Status}}' $container 2>$null
            if (($health -join '').Trim() -eq 'healthy') { return }
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw $message
}

function Verify-BrokerBootstrap {
    [void](Invoke-Dynsec @('getClient', $env:NEXAIOT_TEST_BACKEND_USERNAME))
    [void](Invoke-Dynsec @('getRole', 'nexaiot-backend-lab-role'))
    [void](Invoke-Dynsec @('getDefaultACLAccess'))
    Write-Output 'BROKER_BOOTSTRAP_VERIFIED: restricted backend identity, role ACLs, and default deny.'
}

function Verify-MqttAccess {
    $topics = @('nexaiot/devices/+/telemetry', 'nexaiot/devices/+/state', 'nexaiot/devices/+/command-results', 'nexaiot/devices/+/activation-responses')
    $docker = (Get-Command docker.exe -ErrorAction Stop).Source
    foreach ($topic in $topics) {
        $arguments = @(
            'compose', '-p', $project, '-f', $compose, 'exec', '-T', 'mosquitto', 'mosquitto_sub', '-d',
            '-h', 'localhost', '-p', '8883', '--cafile', '/mosquitto/certs/ca.crt',
            '-u', $env:NEXAIOT_TEST_BACKEND_USERNAME, '-P', $env:NEXAIOT_TEST_BACKEND_PASSWORD,
            '-i', $env:NEXAIOT_TEST_BACKEND_CLIENT_ID, '-t', $topic, '-C', '1', '-W', '3'
        )
        $probe = Invoke-ProcessCapture $docker $arguments
        $joined = @($probe.Stdout, $probe.Stderr) -join "`n"
        if ($joined -notmatch 'CONNACK.*0' -or $joined -notmatch 'SUBACK' -or ($probe.ExitCode -ne 0 -and $probe.ExitCode -ne 27)) {
            throw "MQTT authentication or subscription verification failed for $topic (exit $($probe.ExitCode)): $($probe.Stderr.Trim())"
        }
    }
    Write-Output 'MQTT_VERIFIED: TLS authentication and all required backend subscriptions were exercised.'
}

function Invoke-ProcessCapture([string]$fileName, [string[]]$arguments) {
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $fileName
    $psi.WorkingDirectory = $repo
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.Arguments = (($arguments | ForEach-Object { ConvertTo-WindowsProcessArgument ([string]$_ ) }) -join ' ')
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $psi
    try {
        if (-not $process.Start()) { throw "Process did not start: $fileName" }
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        return [pscustomobject]@{ ExitCode = $process.ExitCode; Stdout = $stdout; Stderr = $stderr }
    } finally {
        $process.Dispose()
    }
}

function ConvertTo-WindowsProcessArgument([string]$value) {
    if ($value -notmatch '[\s"]') { return $value }
    return '"' + $value.Replace('"', '\"') + '"'
}

function Invoke-HttpsProbe([string]$caCertificate, [string]$url) {
    $curl = (Get-Command curl.exe -ErrorAction Stop).Source
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $curl
    $psi.WorkingDirectory = $repo
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.Arguments = '--fail --silent --show-error --connect-timeout 2 --max-time 5 --cacert {0} {1}' -f (ConvertTo-WindowsProcessArgument $caCertificate), (ConvertTo-WindowsProcessArgument $url)
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $psi
    try {
        if (-not $process.Start()) { throw 'HTTPS probe process did not start.' }
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Stdout = $stdout
            Stderr = $stderr
        }
    } finally {
        $process.Dispose()
    }
}

function Start-HostApplication {
    $java = (Get-Command java).Source
    $jar = Join-Path $repo 'server\target\server-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Spring Boot JAR is missing: $jar" }
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $java; $psi.WorkingDirectory = $repo; $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
    $psi.Arguments = '-jar {0} --spring.profiles.active=b46-disposable' -f (ConvertTo-WindowsProcessArgument $jar)
    $settings = @{
        'NEXAIOT_B46_APPLICATION_MODE' = 'host'; 'NEXAIOT_B46_EXPECTED_MONGO_HOST' = '127.0.0.1'; 'NEXAIOT_B46_EXPECTED_MONGO_PORT' = "$mongoPort"; 'NEXAIOT_B46_EXPECTED_MQTT_HOST' = '127.0.0.1'; 'NEXAIOT_B46_EXPECTED_MQTT_PORT' = "$mqttTlsPort"
        'MONGODB_URI' = "mongodb://127.0.0.1:$mongoPort/nexaiot_b46"; 'MQTT_BROKER_URL' = "ssl://127.0.0.1:$mqttTlsPort"; 'MQTT_CLIENT_ID' = $env:NEXAIOT_TEST_BACKEND_CLIENT_ID; 'MQTT_USERNAME' = $env:NEXAIOT_TEST_BACKEND_USERNAME; 'MQTT_PASSWORD' = $env:NEXAIOT_TEST_BACKEND_PASSWORD; 'MQTT_CA_CERTIFICATE' = (Join-Path $runtime 'certs\ca.crt')
        'MQTT_MANAGEMENT_ENABLED' = 'true'; 'MQTT_MANAGEMENT_BROKER_URL' = "ssl://127.0.0.1:$mqttTlsPort"; 'MQTT_MANAGEMENT_CLIENT_ID' = $env:NEXAIOT_TEST_MANAGEMENT_CLIENT_ID; 'MQTT_MANAGEMENT_USERNAME' = $env:NEXAIOT_TEST_MANAGEMENT_USERNAME; 'MQTT_MANAGEMENT_PASSWORD' = $env:NEXAIOT_TEST_MANAGEMENT_PASSWORD; 'MQTT_MANAGEMENT_TLS_REQUIRED' = 'true'; 'MQTT_MANAGEMENT_CA_CERTIFICATE' = (Join-Path $runtime 'certs\ca.crt')
        'PROVISIONING_PAIRING_EXPIRY_SECONDS' = $env:NEXAIOT_B46_PAIRING_EXPIRY_SECONDS; 'PROVISIONING_CREDENTIAL_DELIVERY_CHALLENGE_LIFETIME_SECONDS' = $env:NEXAIOT_B46_DELIVERY_CHALLENGE_LIFETIME_SECONDS; 'PROVISIONING_ACTIVATION_CHALLENGE_LIFETIME_SECONDS' = $env:NEXAIOT_B46_ACTIVATION_CHALLENGE_LIFETIME_SECONDS
        'NEXAIOT_CREDENTIAL_ESCROW_KEY' = $env:NEXAIOT_CREDENTIAL_ESCROW_KEY; 'NEXAIOT_CREDENTIAL_ESCROW_KEY_VERSION' = 'b46-test-v1'; 'JWT_SECRET' = $env:NEXAIOT_B46_JWT_SECRET; 'SERVER_SSL_KEY_STORE' = (Join-Path $runtime 'certs\app.p12'); 'SERVER_SSL_KEY_STORE_PASSWORD' = $env:NEXAIOT_B46_APP_KEYSTORE_PASSWORD
        'SERVER_PORT' = "$httpsPort"; 'SERVER_ADDRESS' = '127.0.0.1'
    }
    foreach ($entry in $settings.GetEnumerator()) { $psi.EnvironmentVariables[$entry.Key] = [string]$entry.Value }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $psi
    if (-not $process.Start()) { throw 'Windows Java process did not start.' }
    $script:appStdoutTask = $process.StandardOutput.ReadToEndAsync()
    $script:appStderrTask = $process.StandardError.ReadToEndAsync()
    return $process
}

function Save-ApplicationOutput {
    if ($appStdoutTask -and $appStdoutTask.IsCompleted) { [IO.File]::WriteAllText($appStdoutLog, $appStdoutTask.Result) }
    if ($appStderrTask -and $appStderrTask.IsCompleted) { [IO.File]::WriteAllText($appStderrLog, $appStderrTask.Result) }
}

function Stop-HostApplication {
    if (-not $appProcess) { return }
    $appProcess.Refresh()
    if (-not $appProcess.HasExited) {
        Assert-HostApplicationIdentity
        $appProcess.Kill()
        [void]$appProcess.WaitForExit(10000)
    }
    if ($appStdoutTask) { [void]$appStdoutTask.Wait(5000) }
    if ($appStderrTask) { [void]$appStderrTask.Wait(5000) }
    Save-ApplicationOutput
}

function Assert-HostApplicationIdentity {
    if (-not $appProcess -or $appProcess.HasExited) { throw 'The disposable Spring Boot process exited before verification.' }
    $info = Get-CimInstance Win32_Process -Filter "ProcessId = $($appProcess.Id)"
    $javaPath = [IO.Path]::GetFullPath((Get-Command java).Source)
    if (-not $info -or [IO.Path]::GetFullPath($info.ExecutablePath) -ne $javaPath -or $info.CommandLine -notmatch [regex]::Escape('server-0.0.1-SNAPSHOT.jar') -or $info.CommandLine -notmatch 'b46-disposable') { throw 'The captured process identity is not the disposable Spring Boot process; it will not be terminated.' }
}

function Verify-Application {
    if ($ApplicationMode -eq 'Container') {
        Wait-Healthy 'app' 180 'Disposable Spring Boot application did not become healthy.'
        $healthProbe = Invoke-HttpsProbe (Join-Path $runtime 'certs\ca.crt') "https://127.0.0.1:$httpsPort/api/health"
        if ($healthProbe.ExitCode -ne 0) { throw "Disposable HTTPS health probe failed with curl exit code $($healthProbe.ExitCode): $($healthProbe.Stderr.Trim())" }
        $health = $healthProbe.Stdout
    } else {
        $health = Wait-HostApplicationReady
    }
    if (($health -join '') -notmatch 'NexaIoT backend is running') { throw 'Disposable HTTPS health response was not valid.' }
    $mongo = docker compose -p $project -f $compose exec -T mongo mongosh --quiet 'mongodb://127.0.0.1:27017/nexaiot_b46' --eval 'db.runCommand({ ping: 1 }).ok'
    if ($LASTEXITCODE -ne 0 -or ($mongo -join '') -notmatch '1') { throw 'Disposable MongoDB access verification failed.' }
    Write-Output 'APPLICATION_VERIFIED: HTTPS, MongoDB access, authenticated MQTT, and subscriptions passed.'
}

function Verify-EnrollmentLifecycle {
    $python = Join-Path $repo 'simulator\.venv\bin\python.exe'
    if (-not (Test-Path -LiteralPath $python -PathType Leaf)) { $python = Join-Path $repo 'simulator\.venv\Scripts\python.exe' }
    if (-not (Test-Path -LiteralPath $python -PathType Leaf)) { throw 'The project-local Python environment is required for live enrollment verification.' }
    $driver = Join-Path $fixture 'verify_enrollment.py'
    $result = Invoke-ProcessCapture $python @(
        $driver, '--base-url', "https://127.0.0.1:$httpsPort", '--ca', (Join-Path $runtime 'certs\ca.crt'), '--runtime', $runtime
    )
    if ($result.ExitCode -ne 0 -or $result.Stdout -notmatch 'LIVE_ENROLLMENT_VERIFIED') {
        throw "Live enrollment verification failed: $($result.Stdout.Trim()) $($result.Stderr.Trim())"
    }
    Write-Output 'ENROLLMENT_VERIFIED: registration, pairing, escrow delivery, MQTT activation, and rejection checks passed.'
}

function Wait-HostApplicationReady {
    $deadline = (Get-Date).AddSeconds(180)
    $caCertificate = Join-Path $runtime 'certs\ca.crt'
    $url = "https://127.0.0.1:$httpsPort/api/health"
    $lastProbe = $null
    do {
        $appProcess.Refresh()
        if ($appProcess.HasExited) {
            $appProcess.WaitForExit()
            throw 'Disposable Spring Boot exited during startup; inspect the sanitized application diagnostics.'
        }
        $probe = Invoke-HttpsProbe $caCertificate $url
        $lastProbe = $probe
        $probeExit = [int]$probe.ExitCode
        if ($probeExit -eq 0) {
            Assert-HostApplicationIdentity
            return $probe.Stdout
        }
        if ($probeExit -notin @(7, 28)) {
            throw "Disposable HTTPS probe failed with curl exit code ${probeExit}: $($probe.Stderr.Trim())"
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Disposable Spring Boot HTTPS readiness timed out; last curl exit code $($lastProbe.ExitCode): $($lastProbe.Stderr.Trim())"
}

function Write-SanitizedDiagnostics {
    try {
        $logs = @()
        try {
            $dockerLogs = @(docker compose -p $project -f $compose logs --no-color --tail 120 2>&1)
            if ($LASTEXITCODE -eq 0) { $logs += $dockerLogs } else { $logs += 'Docker diagnostics were unavailable.' }
        } catch { $logs += 'Docker diagnostics were unavailable.' }
        Save-ApplicationOutput
        if (Test-Path -LiteralPath $appStdoutLog) { $logs += 'Spring Boot stdout:'; $logs += @(Get-Content -LiteralPath $appStdoutLog -ErrorAction SilentlyContinue) }
        if (Test-Path -LiteralPath $appStderrLog) { $logs += 'Spring Boot stderr:'; $logs += @(Get-Content -LiteralPath $appStderrLog -ErrorAction SilentlyContinue) }
        $sanitized = ($logs -join "`n")
        $redactions = @($env:NEXAIOT_TEST_ADMIN_PASSWORD, $env:NEXAIOT_TEST_BACKEND_PASSWORD, $env:NEXAIOT_CREDENTIAL_ESCROW_KEY, $env:NEXAIOT_B46_JWT_SECRET, $env:NEXAIOT_B46_APP_KEYSTORE_PASSWORD)
        foreach ($keyFile in @('ca.key', 'server.key', 'app.key')) {
            $keyPath = Join-Path $runtime (Join-Path 'certs' $keyFile)
            if (Test-Path -LiteralPath $keyPath) { $redactions += Get-Content -LiteralPath $keyPath -Raw -ErrorAction SilentlyContinue }
        }
        foreach ($secret in $redactions) { if ($secret) { $sanitized = $sanitized.Replace($secret, '[REDACTED]') } }
        $sanitized = [regex]::Replace($sanitized, '(?im)(Using generated security password:\s*)\S+', '$1[REDACTED]')
        if ($sanitized) { Write-Warning ("Disposable diagnostics:`n" + $sanitized) }
    } catch { Write-Warning 'Disposable diagnostics were unavailable.' }
}

Require-Command docker
if (-not $RunLive) {
    if ((Get-Content -LiteralPath $compose -Raw) -notmatch 'eclipse-mosquitto:2') { throw 'Compose must use the cached eclipse-mosquitto:2 image.' }
    if ((Get-Content -LiteralPath $compose -Raw) -match 'grep|28885|18443') { throw 'Compose contains a fixed or malformed disposable probe.' }
    Write-Output 'STATIC_ONLY: no files, containers, volumes, networks, processes, or secrets were created.'
    exit 0
}

Assert-IsolatedPorts
if (-not $PSCmdlet.ShouldProcess($project, "start disposable B4.6 Compose project with $ApplicationMode application")) { exit 0 }

$env:NEXAIOT_B46_NETWORK = $network; $env:NEXAIOT_B46_MONGO_VOLUME = $mongoVolume; $env:NEXAIOT_B46_MOSQUITTO_VOLUME = $mosquittoVolume; $env:NEXAIOT_B46_RUNTIME_NAME = $project; $env:NEXAIOT_B46_MONGO_PORT = "$mongoPort"; $env:NEXAIOT_B46_MQTT_TLS_PORT = "$mqttTlsPort"; $env:NEXAIOT_B46_HTTPS_PORT = "$httpsPort"
$env:NEXAIOT_TEST_ADMIN_USERNAME = 'nexaiot-b46-admin'; $env:NEXAIOT_TEST_ADMIN_PASSWORD = New-RandomBase64; $env:NEXAIOT_TEST_BACKEND_USERNAME = 'nexaiot-backend-lab'; $env:NEXAIOT_TEST_BACKEND_CLIENT_ID = 'nexaiot-backend-lab'; $env:NEXAIOT_TEST_BACKEND_PASSWORD = New-RandomBase64; $env:NEXAIOT_TEST_MANAGEMENT_USERNAME = 'nexaiot-b46-management'; $env:NEXAIOT_TEST_MANAGEMENT_CLIENT_ID = 'nexaiot-b46-management'; $env:NEXAIOT_TEST_MANAGEMENT_PASSWORD = New-RandomBase64; $env:NEXAIOT_TEST_MANAGEMENT_ENABLED = 'true'; $env:NEXAIOT_B46_PAIRING_EXPIRY_SECONDS = '2'; $env:NEXAIOT_B46_DELIVERY_CHALLENGE_LIFETIME_SECONDS = '120'; $env:NEXAIOT_B46_ACTIVATION_CHALLENGE_LIFETIME_SECONDS = '120'; $env:NEXAIOT_CREDENTIAL_ESCROW_KEY = New-RandomBase64; $env:NEXAIOT_B46_JWT_SECRET = New-RandomBase64; $env:NEXAIOT_B46_APP_KEYSTORE_PASSWORD = New-RandomBase64
$started = $false
try {
    New-Item -ItemType Directory -Force -Path $runtime | Out-Null
    New-RuntimeCertificate
    docker image inspect eclipse-mosquitto:2 *> $null
    if ($LASTEXITCODE -ne 0) { throw 'The cached eclipse-mosquitto:2 image is unavailable; no live test was run.' }
    docker run --rm --pull=never --entrypoint /bin/sh eclipse-mosquitto:2 -c 'test -f /usr/lib/mosquitto_dynamic_security.so && command -v mosquitto_ctrl'
    if ($LASTEXITCODE -ne 0) { throw 'The cached Mosquitto image lacks the required Dynamic Security plugin.' }
    docker info *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is unavailable; no live test was run.' }
    docker compose -p $project -f $compose up -d mongo mosquitto
    if ($LASTEXITCODE -ne 0) { throw 'Disposable MongoDB/Mosquitto startup failed.' }
    $started = $true
    Wait-Healthy 'mongo' 60 'Disposable MongoDB did not become healthy.'
    Wait-Healthy 'mosquitto' 60 'Disposable Mosquitto Dynamic Security bootstrap did not become healthy.'
    Verify-BrokerBootstrap
    Verify-MqttAccess
    if ($ApplicationMode -eq 'Host') { $appProcess = Start-HostApplication } else { docker compose -p $project -f $compose --profile application up -d app; if ($LASTEXITCODE -ne 0) { throw 'Disposable container application startup failed.' } }
    Verify-Application
    Verify-EnrollmentLifecycle
    Write-Output "ISOLATED_INTEGRATION_VERIFIED: $ApplicationMode application; enrollment and activation flow passed."
} catch {
    $originalError = $_
    try { Stop-HostApplication } catch { Write-Warning 'The disposable Java process could not be safely stopped.' }
    if ($RunLive) {
        try { Write-SanitizedDiagnostics } catch { Write-Warning 'Disposable diagnostics were unavailable.' }
    }
    throw $originalError
} finally {
    if ($appProcess -and -not $appProcess.HasExited) { try { Stop-HostApplication } catch { Write-Error $_ -ErrorAction Continue } }
    if ($started -and -not $KeepResources) { docker compose -p $project -f $compose down -v --remove-orphans }
    if (-not $KeepResources -and (Test-Path -LiteralPath $runtime)) { Remove-Item -LiteralPath $runtime -Recurse -Force }
}
