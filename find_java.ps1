$paths = @(
    "C:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\Android\Android Studio\jre",
    "$env:LOCALAPPDATA\Android\Sdk\jre",
    "$env:PROGRAMFILES\Android\Android Studio\jbr",
    "$env:PROGRAMFILES\Android\Android Studio\jre"
)

$found = $false
foreach ($p in $paths) {
    if (Test-Path "$p\bin\java.exe") {
        Write-Output "JAVA_HOME_PATH: $p"
        $found = $true
        break
    }
}

if (!$found) {
    Write-Output "JAVA_NOT_FOUND"
}
