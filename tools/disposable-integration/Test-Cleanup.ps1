$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot 'Run-DisposableIntegration.ps1'
$source = Get-Content -LiteralPath $sourcePath -Raw
$match = [regex]::Match($source, '(?s)function Remove-DisposableRuntime\s*\{.*?\n\}')
if (-not $match.Success) { throw 'Cleanup function was not found in the integration harness.' }
Invoke-Expression $match.Value

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

$root = Join-Path ([IO.Path]::GetTempPath()) ('nexaiot-cleanup-test-' + [guid]::NewGuid().ToString('N'))
$runtime = Join-Path $root 'current-run'
$other = Join-Path $root 'other-run'
New-Item -ItemType Directory -Force -Path $runtime,$other | Out-Null
try {
    $KeepResources = $false
    $result = Remove-DisposableRuntime
    Assert-True ($result -eq $true) 'Successful cleanup did not return true.'
    Assert-True (-not (Test-Path -LiteralPath $runtime)) 'Current runtime path still exists after cleanup.'
    Assert-True (Test-Path -LiteralPath $other) 'Cleanup touched a different runtime path.'

    New-Item -ItemType Directory -Force -Path $runtime | Out-Null
    function Remove-Item { throw 'simulated removal failure' }
    $failed = $false
    try { [void](Remove-DisposableRuntime) } catch { $failed = $_.Exception.Message -match 'simulated removal failure|still exists' }
    Assert-True $failed 'Removal failure was not reported.'

    $KeepResources = $true
    Assert-True ((Remove-DisposableRuntime) -eq $false) 'KeepResources incorrectly reported cleanup success.'
    Assert-True (Test-Path -LiteralPath $runtime) 'KeepResources unexpectedly removed the runtime path.'

    $harness = Get-Content -LiteralPath $sourcePath -Raw
    Assert-True ($harness -match 'Disposable Compose cleanup failed') 'Compose teardown failure is not reported.'
    Assert-True ($harness -match '\$runFailure -and \$cleanupFailures\.Count') 'Combined lifecycle and cleanup failures are not preserved.'
    Assert-True ($harness -match 'runtime cleanup verified') 'Success marker does not identify verified cleanup.'
    Assert-True ($harness.IndexOf('runtime cleanup verified') -gt $harness.IndexOf('Remove-DisposableRuntime')) 'Success marker appears before cleanup verification.'
    Write-Output 'CLEANUP_BEHAVIOR_TESTS_PASSED'
} finally {
    if ([IO.Directory]::Exists($root)) { [IO.Directory]::Delete($root, $true) }
}
