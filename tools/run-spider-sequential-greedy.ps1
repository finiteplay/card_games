param(
    [Parameter(Mandatory = $true)][string]$Classpath,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [int]$SecondsPerSeed = 3600,
    [int]$Workers = 16
)

$ErrorActionPreference = 'Stop'

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class AwakeState {
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern uint SetThreadExecutionState(uint flags);
}
'@

$continuous = [uint32]2147483648
$systemRequired = [uint32]0x00000001
$previous = [AwakeState]::SetThreadExecutionState($continuous -bor $systemRequired)
if ($previous -eq 0) {
    throw 'SetThreadExecutionState failed; refusing to run without sleep prevention.'
}

try {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $java = 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe'
    $arguments = @(
        '-Xmx2g',
        '-XX:ActiveProcessorCount=16',
        '-cp', $Classpath,
        'org.finiteplay.spider.solver.FourSuitSequentialGreedy',
        $OutputDirectory,
        $SecondsPerSeed,
        $Workers
    )
    $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory (Get-Location).Path `
        -WindowStyle Hidden -RedirectStandardOutput (Join-Path $OutputDirectory 'stdout.log') `
        -RedirectStandardError (Join-Path $OutputDirectory 'stderr.log') -PassThru -Wait
    $process.ExitCode | Set-Content (Join-Path $OutputDirectory 'exit-code.txt')
    exit $process.ExitCode
}
finally {
    [void][AwakeState]::SetThreadExecutionState($continuous)
}
