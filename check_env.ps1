$sdkPath = "$env:USERPROFILE\AppData\Local\Android\Sdk"
$sdkExists = Test-Path $sdkPath
Write-Output "SDK_EXIST: $sdkExists"

$desktopOutput = "$env:USERPROFILE\Desktop\output"
$desktopExists = Test-Path $desktopOutput
if (!$desktopExists) {
    $desktopOutput = "$env:USERPROFILE\OneDrive\바탕 화면\output"
    $desktopExists = Test-Path $desktopOutput
}
Write-Output "DESKTOP_OUTPUT_EXIST: $desktopExists"
Write-Output "DESKTOP_OUTPUT_PATH: $desktopOutput"

$gradleExists = $null -ne (Get-Command gradle -ErrorAction SilentlyContinue)
Write-Output "GRADLE_AVAILABLE: $gradleExists"
