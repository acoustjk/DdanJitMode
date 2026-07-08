# Git 초기화 및 브랜치 설정
if (!(Test-Path ".git")) {
    Write-Host "Initializing local git repository..."
    git init
    git branch -M main
} else {
    Write-Host "Git repository already initialized."
}

# 원격 저장소 설정 (origin 등록)
$remotes = git remote
if ($remotes -notcontains "origin") {
    Write-Host "Adding remote origin..."
    git remote add origin https://github.com/acoustjk/DdanJitMode
} else {
    Write-Host "Updating remote origin URL..."
    git remote set-url origin https://github.com/acoustjk/DdanJitMode
}

# Git 사용자 설정 확인 및 임시 설정 (이메일/이름 누락으로 커밋 에러 방지)
$gitEmail = git config --global user.email
$gitName = git config --global user.name

if ([string]::IsNullOrEmpty($gitEmail)) {
    Write-Host "Global Git email not found. Setting temporary email..."
    git config user.email "developer@example.com"
}
if ([string]::IsNullOrEmpty($gitName)) {
    Write-Host "Global Git name not found. Setting temporary name..."
    git config user.name "DdanJitModeDeveloper"
}

# 변경사항 스테이징 및 커밋 생성
Write-Host "Staging files..."
git add -A

Write-Host "Creating commit..."
git commit -m "Initialize DdanJitMode MVP project with real-time UTIC API integration, dual signal overlay widgets, and demo simulator"

# GitHub로 푸시 진행 (인증 캐시가 활성화되어 있으면 무중단 진행됨)
Write-Host "Pushing to remote repository (main)..."
git push -u origin main --force

if ($LASTEXITCODE -eq 0) {
    Write-Host "Git Push Successful!"
} else {
    Write-Error "Git Push Failed! Please verify your GitHub credentials/permissions or if you have a Personal Access Token (PAT) configured."
}
