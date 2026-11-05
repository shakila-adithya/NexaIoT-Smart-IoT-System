[CmdletBinding(SupportsShouldProcess)]
param(
    [ValidateSet('Start', 'Status', 'Verify', 'Stop', 'Help')]
    [string]$Action = 'Start'
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent (Split-Path -Parent $root)
$client = Join-Path $repo 'client'
$runtime = Join-Path $root '.runtime'
$envFile = Join-Path $runtime 'compose.env'
$compose = Join-Path $root 'docker-compose.yml'
$project = 'nexaiot-local-dev'
$stateFile = Join-Path $runtime 'demo-preview.json'
$frontend = 'http://127.0.0.1:4173'
$backend = 'https://127.0.0.1:8443'
$mqttHost = '127.0.0.1'
$mqttPort = 8885

function Require-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) { throw "$name is required." }
}

function Get-Python {
    $path = Join-Path $repo 'simulator\.venv\bin\python.exe'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { $path = Join-Path $repo 'simulator\.venv\Scripts\python.exe' }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'The project-local simulator Python environment is required.' }
    return $path
}

function Get-PortListener([int]$port) {
    Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
}

function Get-ContainerHealth([string]$service) {
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) { return 'unknown' }
    $id = (& docker compose --env-file $envFile -p $project -f $compose ps -q $service 2>$null | Out-String).Trim()
    if (-not $id) { return 'stopped' }
    $health = (& docker inspect --format '{{.State.Health.Status}}' $id 2>$null | Out-String).Trim()
    if ($health) { return $health }
    return 'unknown'
}

function Get-PreviewProcess {
    if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) { return $null }
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $process = Get-Process -Id ([int]$state.Pid) -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    $cim = Get-CimInstance Win32_Process -Filter "ProcessId=$($state.Pid)" -ErrorAction SilentlyContinue
    $node = (Get-Command node.exe -ErrorAction Stop).Source
    $vite = Join-Path $client 'node_modules\vite\bin\vite.js'
    if ((-not $cim) -or ($cim.ExecutablePath -ne $node) -or ($cim.CommandLine -notmatch [regex]::Escape($vite)) -or ($cim.CommandLine -notmatch 'preview') -or ($cim.CommandLine -notmatch '--port 4173')) {
        throw 'The recorded frontend process is not the owned local preview; refusing to stop it.'
    }
    return $process
}

function Test-BackendHealth {
    $python = Get-Python
    $ca = Join-Path $runtime 'certs\ca.crt'
    & $python (Join-Path $root 'verify_local_demo.py') --ca $ca --backend $backend --frontend $frontend --mqtt-host $mqttHost --mqtt-port $mqttPort
    if ($LASTEXITCODE -ne 0) { throw 'Local demo verification failed.' }
}

function Test-FrontendBuild {
    $assets = Join-Path $client 'dist\assets'
    if (-not (Test-Path -LiteralPath (Join-Path $client 'dist\index.html'))) { throw 'The production frontend build is missing.' }
    $apiMatch = Get-ChildItem -LiteralPath $assets -Filter '*.js' -File | Select-String -SimpleMatch 'https://127.0.0.1:8443' | Select-Object -First 1
    if (-not $apiMatch) { throw 'The production frontend build does not contain the required HTTPS API URL.' }
}

function Build-Frontend {
    $previous = $env:VITE_API_URL
    $env:VITE_API_URL = $backend
    try {
        Push-Location $client
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend production build failed.' }
    } finally {
        Pop-Location
        if ($null -eq $previous) { Remove-Item Env:VITE_API_URL -ErrorAction SilentlyContinue } else { $env:VITE_API_URL = $previous }
    }
    Test-FrontendBuild
}

function Start-Preview {
    $existing = Get-PreviewProcess
    if ($existing) { return }
    if (Get-PortListener 4173) { throw 'Port 4173 is already in use by an unowned process.' }
    $node = (Get-Command node.exe -ErrorAction Stop).Source
    $vite = Join-Path $client 'node_modules\vite\bin\vite.js'
    $stdout = Join-Path $runtime 'demo-preview.stdout.log'
    $stderr = Join-Path $runtime 'demo-preview.stderr.log'
    $arguments = '"{0}" preview --host 127.0.0.1 --port 4173 --strictPort' -f $vite
    $process = Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $client `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    if (-not $process) { throw 'Frontend preview did not start.' }
    Set-Content -LiteralPath $stateFile -Value (@{ Pid = $process.Id; Node = $node; Vite = $vite } | ConvertTo-Json) -Encoding UTF8 -NoNewline
}

function Stop-Preview {
    if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) { return }
    $process = Get-PreviewProcess
    if ($process -and -not $process.HasExited) { $process.Kill(); [void]$process.WaitForExit(10000) }
    Remove-Item -LiteralPath $stateFile -Force
}

function Get-PortState([int]$port) {
    if (Get-PortListener $port) { return 'listening' }
    return 'stopped'
}

function Get-PreviewState {
    try {
        if (Get-PreviewProcess) { return 'owned and running' }
    } catch {
        return 'unverified process on port'
    }
    if (Get-PortListener 4173) { return 'unknown process on port' }
    return 'stopped'
}

function Show-Status {
    Write-Output ("MongoDB: " + (Get-ContainerHealth 'mongo'))
    Write-Output ("Mosquitto TLS: " + (Get-ContainerHealth 'mosquitto'))
    Write-Output ("Backend: " + (Get-PortState 8443))
    Write-Output ("Frontend preview: " + (Get-PreviewState))
    Write-Output ("Development frontend: " + (Get-PortState 5174))
    Write-Output ("MQTT TLS: " + (Get-PortState 8885))
}

function Show-Help {
    Write-Output 'Start:  Run-LocalDemo.ps1 -Action Start'
    Write-Output 'Status: Run-LocalDemo.ps1 -Action Status'
    Write-Output 'Verify: Run-LocalDemo.ps1 -Action Verify'
    Write-Output 'Stop:   Run-LocalDemo.ps1 -Action Stop'
}

if ($Action -eq 'Help') { Show-Help; exit 0 }
if ($Action -eq 'Status') { Show-Status; exit 0 }
if (-not $PSCmdlet.ShouldProcess('NexaIoT local demo', $Action)) { exit 0 }

Require-Command docker
Require-Command node.exe
Require-Command npm.cmd
if ($Action -eq 'Stop') { Stop-Preview; exit 0 }

if ($Action -eq 'Start') {
    if ((Get-ContainerHealth 'mongo') -ne 'healthy' -or (Get-ContainerHealth 'mosquitto') -ne 'healthy') {
        if ((Get-PortListener 8443) -or (Get-PortListener 27018) -or (Get-PortListener 8885)) { throw 'Required local services are not healthy and an existing process owns a required port.' }
        & (Join-Path $root 'Run-LocalDevelopment.ps1') -Action Start
        if ($LASTEXITCODE -ne 0) { throw 'Existing local development startup failed.' }
    }
    Build-Frontend
    Start-Preview
    Start-Sleep -Seconds 2
    Test-BackendHealth
    Test-FrontendBuild
    Write-Output 'LOCAL_DEMO_FRONTEND: http://127.0.0.1:4173'
    Write-Output 'LOCAL_DEMO_BACKEND: https://127.0.0.1:8443'
    Write-Output 'LOCAL_DEMO_MQTT: ssl://127.0.0.1:8885'
    Write-Output 'LOCAL_DEMO_CA: tools/local-development/.runtime/certs/ca.crt'
    exit 0
}

if ($Action -eq 'Verify') {
    Test-FrontendBuild
    Test-BackendHealth
    if (-not (Get-PreviewProcess)) { throw 'Owned frontend preview is not running.' }
    Write-Output 'LOCAL_DEMO_VERIFY: passed'
    exit 0
}
