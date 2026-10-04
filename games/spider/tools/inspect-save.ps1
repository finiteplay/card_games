<#
.SYNOPSIS
    Pulls the active Spider save off a connected device and prints the board it decodes to.

.DESCRIPTION
    Dev tool, not part of the app. Requires a device connected via adb (USB or wireless) with
    the Spider debug/dev build installed and debuggable, so `run-as` can read its data dir.

    Uses `adb exec-out` (not `adb shell ... >`), because piping `adb shell` output through a
    Windows/Git-Bash redirect corrupts the binary file: every 0x0A byte gets a spurious 0x0D
    inserted before it (text-mode newline translation), and it decodes into garbage.

.PARAMETER Serial
    adb device serial, if more than one device is attached.
#>
param(
    [string]$Serial
)

$ErrorActionPreference = "Stop"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

$serialArg = if ($Serial) { "-s $Serial" } else { "" }

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$savePath = Join-Path $env:TEMP "spider_active_game.preferences_pb"

# A true OS-level redirect (via cmd.exe), not PowerShell's own `>` or Set-Content — both of
# those decode adb's stdout as text first, which corrupts the binary file the same way a
# Git-Bash redirect does (see the .DESCRIPTION note above).
& cmd.exe /c "`"$adb`" $serialArg exec-out run-as org.finiteplay.spider cat files/active_game.preferences_pb > `"$savePath`""

if (-not (Test-Path $savePath) -or (Get-Item $savePath).Length -eq 0) {
    throw "Pulled file is empty — is a device connected with the Spider dev build installed?"
}

& "$repoRoot\gradlew.bat" ":games:spider:rules:inspectSave" "-Psave=$savePath" --quiet --console=plain
