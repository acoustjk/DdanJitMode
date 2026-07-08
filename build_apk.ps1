# 1. JAVA_HOME 설정
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "JAVA_HOME set to: $env:JAVA_HOME"
Write-Host "Starting Android APK Build (this may take a few minutes for downloading dependencies)..."

# 2. Gradle 빌드 시작
.\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "Gradle Build Successful!"
    
    # 3. APK 복사 대상 확인 및 복사
    $apkSource = "app/build/outputs/apk/debug/app-debug.apk"
    $desktopOutput = "$env:USERPROFILE\Desktop\output"
    if (!(Test-Path $desktopOutput)) {
        $desktopOutput = "$env:USERPROFILE\OneDrive\바탕 화면\output"
    }

    if (Test-Path $apkSource) {
        $targetPath = Join-Path $desktopOutput "DdanJitMode-debug.apk"
        Copy-Item -Path $apkSource -Destination $targetPath -Force
        Write-Host "APK successfully copied to: $targetPath"
    } else {
        Write-Error "Build succeeded, but APK file could not be found at $apkSource"
    }
} else {
    Write-Error "Gradle Build Failed! Please check the output log."
}
