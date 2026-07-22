param([ValidateSet('All','Mobile','Tv')][string]$Variant = 'All')

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$workspaceRoot = (Resolve-Path (Join-Path $projectRoot '..')).Path
$gradle = Join-Path $workspaceRoot 'android-toolchain\gradle\gradle-8.9\bin\gradle.bat'
$androidJbr = 'C:\Program Files\Android\Android Studio\jbr'
$gradleJdk = Join-Path $workspaceRoot 'android-toolchain\jdk\jdk-21.0.11+10'
if (-not (Test-Path -LiteralPath $androidJbr)) { throw "Android-Studio-JBR fehlt: $androidJbr" }

$env:JAVA_HOME = $gradleJdk
$env:ANDROID_HOME = Join-Path $workspaceRoot 'android-toolchain\sdk'
$env:Path = "$gradleJdk\bin;$env:Path"
$env:GRADLE_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'
$variants = if ($Variant -eq 'All') { @('MobileDebug','TvDebug') } else { @("${Variant}Debug") }

Push-Location $projectRoot
try {
    $exportTasks = $variants | ForEach-Object { ":app:export${_}Javac" }
    & $gradle $exportTasks --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Gradle konnte die Compiler-Konfiguration nicht exportieren.' }

    foreach ($name in $variants) {
        $meta = Join-Path $projectRoot 'manual\gradle-javac'
        $classPath = (Get-Content -LiteralPath (Join-Path $meta "$name.classpath") -Raw).Trim()
        $rjar = ($classPath -split ';' | Where-Object { $_ -match '\\R\.jar$' } | Select-Object -First 1)
        if ($rjar -and (Test-Path -LiteralPath $rjar)) {
            $rCopy = Join-Path $meta "$name-R-copy.jar"
            Copy-Item -LiteralPath $rjar -Destination $rCopy -Force
            $classPath = $classPath.Replace($rjar, $rCopy)
        }
        $destination = (Get-Content -LiteralPath (Join-Path $meta "$name.destination") -Raw).Trim()
        $sources = Get-Content -LiteralPath (Join-Path $meta "$name.sources")
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        $argFile = Join-Path $meta "$name.args"
        $arguments = @('-encoding','UTF-8','-source','17','-target','17','-classpath',('"' + ($classPath -replace '\\','/') + '"'),'-d',('"' + ($destination -replace '\\','/') + '"'))
        $arguments += $sources | ForEach-Object { '"' + ($_ -replace '\\','/') + '"' }
        Set-Content -LiteralPath $argFile -Value $arguments -Encoding ASCII
        & (Join-Path $androidJbr 'bin\javac.exe') "@$argFile"
        if ($LASTEXITCODE -ne 0) { throw "javac schlug für $name fehl." }
    }

    $assembleTasks = $variants | ForEach-Object { ":app:assemble${_}" }
    & $gradle $assembleTasks -PexternalSandboxJavac --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'APK-Paketierung fehlgeschlagen.' }
} finally {
    Pop-Location
}
