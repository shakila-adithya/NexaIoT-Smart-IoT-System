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
    $marker = 'ISOLATED_INTEGRATION_VERIFIED:'
    Assert-True (($harness -split [Environment]::NewLine | Where-Object { $_ -match [regex]::Escape($marker) }).Count -eq 1) 'Success marker is not emitted exactly once.'
    Assert-True ($harness.IndexOf($marker) -gt $harness.IndexOf('Remove-DisposableRuntime')) 'Success marker appears before cleanup verification.'
    Assert-True ($harness.IndexOf($marker) -gt $harness.IndexOf('if ($cleanupFailures.Count)')) 'Success marker can bypass cleanup failure handling.'
    Assert-True ($harness -match '\$result\.ExitCode -ne 0' -and $harness -match 'LIVE_ENROLLMENT_VERIFIED') 'Enrollment driver failure is not gated.'
    Write-Output 'CLEANUP_BEHAVIOR_TESTS_PASSED'
} finally {
    if ([IO.Directory]::Exists($root)) { [IO.Directory]::Delete($root, $true) }
}
