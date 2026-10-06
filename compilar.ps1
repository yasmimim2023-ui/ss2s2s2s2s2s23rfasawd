param([switch]$Release)
$ErrorActionPreference = 'Stop'
$taskProject = $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $taskJbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path -LiteralPath (Join-Path $taskJbr 'bin\java.exe')) { $env:JAVA_HOME = $taskJbr }
    elseif (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Configure JAVA_HOME com JDK 17 ou o JDK do Android Studio.' }
}
if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT -and -not (Test-Path -LiteralPath (Join-Path $taskProject 'local.properties'))) {
    $taskSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path -LiteralPath $taskSdk) { $env:ANDROID_HOME = $taskSdk }
    else { throw 'Abra o projeto no Android Studio para configurar o SDK, ou defina ANDROID_HOME.' }
}
if ($Release -and -not (Test-Path -LiteralPath (Join-Path $taskProject 'keystore.properties'))) { throw 'Release exige seu keystore.properties. Veja README.md.' }
$taskBuild = if ($Release) { 'Release' } else { 'Debug' }
Push-Location -LiteralPath $taskProject
try {
    & .\gradlew.bat "assembleController$taskBuild" "assembleReceiver$taskBuild" testControllerDebugUnitTest testReceiverDebugUnitTest lintControllerDebug lintReceiverDebug
    if ($LASTEXITCODE -ne 0) { throw 'Build/testes/lint falharam; revise a saída acima.' }
    $taskDist = Join-Path $taskProject 'dist'
    New-Item -ItemType Directory -Force -Path $taskDist | Out-Null
    $taskSuffix = $taskBuild.ToLowerInvariant()
    Copy-Item -LiteralPath (Join-Path $taskProject "app\build\outputs\apk\controller\$taskSuffix\app-controller-$taskSuffix.apk") -Destination (Join-Path $taskDist 'ThorLink-Controle.apk')
    Copy-Item -LiteralPath (Join-Path $taskProject "app\build\outputs\apk\receiver\$taskSuffix\app-receiver-$taskSuffix.apk") -Destination (Join-Path $taskDist 'ThorLink-Receptor.apk')
    Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $taskDist 'ThorLink-Controle.apk'),(Join-Path $taskDist 'ThorLink-Receptor.apk')
    Write-Host "APKs gerados em $taskDist"
} finally { Pop-Location }
