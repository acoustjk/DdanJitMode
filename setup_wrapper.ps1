# Gradle Wrapper 디렉토리 생성
$wrapperDir = "gradle/wrapper"
if (!(Test-Path $wrapperDir)) {
    New-Item -ItemType Directory -Path $wrapperDir -Force | Out-Null
}

# 1. gradlew (Unix script) 다운로드
Write-Host "Downloading gradlew..."
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradlew" -OutFile "gradlew"

# 2. gradlew.bat (Windows batch) 다운로드
Write-Host "Downloading gradlew.bat..."
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradlew.bat" -OutFile "gradlew.bat"

# 3. gradle-wrapper.jar 다운로드
Write-Host "Downloading gradle-wrapper.jar..."
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar" -OutFile "gradle/wrapper/gradle-wrapper.jar"

# 4. gradle-wrapper.properties 작성
Write-Host "Writing gradle-wrapper.properties..."
$propertiesContent = @"
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"@
Set-Content -Path "gradle/wrapper/gradle-wrapper.properties" -Value $propertiesContent

Write-Host "Gradle Wrapper Setup Completed Successfully!"
