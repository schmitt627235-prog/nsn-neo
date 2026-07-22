param([ValidateSet('mobile','tv')][string]$Flavor)
$ErrorActionPreference='Stop'
$project=(Resolve-Path $PSScriptRoot).Path
$root=(Resolve-Path (Join-Path $project '..\..')).Path
$tool=(Resolve-Path (Join-Path $project '..\android-toolchain')).Path
$jdk=(Resolve-Path (Join-Path $tool 'jdk\jdk-21.0.11+10')).Path
$sdk=(Resolve-Path (Join-Path $tool 'sdk')).Path
$bt=Join-Path $sdk 'build-tools\35.0.0'
$androidJar=Join-Path $sdk 'platforms\android-35\android.jar'
$env:JAVA_HOME=$jdk
$env:ANDROID_SDK_ROOT=$sdk
$build=Join-Path $project ("manual\build-v4-"+$Flavor)
$out=Join-Path $root 'outputs'
$manifest=Join-Path $project ("manual\"+$Flavor+"-manifest.xml")

New-Item -ItemType Directory -Force $build,(Join-Path $build 'gen'),(Join-Path $build 'classes'),(Join-Path $build 'dex') | Out-Null
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $project 'app\src\main\res') -o (Join-Path $build 'resources.zip')
if($LASTEXITCODE){throw 'aapt2 compile failed'}
$appId=$(if($Flavor -eq 'tv'){'de.nsn.neo.tv'}else{'de.nsn.neo.mobile'})
& (Join-Path $bt 'aapt2.exe') link -o (Join-Path $build 'unsigned.apk') -I $androidJar --manifest $manifest --rename-manifest-package $appId --java (Join-Path $build 'gen') --min-sdk-version 26 --target-sdk-version 35 (Join-Path $build 'resources.zip')
if($LASTEXITCODE){throw 'aapt2 link failed'}

$sourceRoots=@(
  (Join-Path $project 'core-model\src\main\java'),
  (Join-Path $project 'core-source-api\src\main\java'),
  (Join-Path $project 'core-session\src\main\java'),
  (Join-Path $project 'source-aniworld\src\main\java'),
  (Join-Path $project 'source-serienstreams\src\main\java'),
  (Join-Path $project 'source-filmpalast\src\main\java'),
  (Join-Path $project 'app\src\main\java'),
  (Join-Path $project ("app\src\"+$Flavor+"\java")),
  (Join-Path $build 'gen')
)
$sources=@($sourceRoots | ForEach-Object {Get-ChildItem $_ -Recurse -Filter '*.java'} | ForEach-Object FullName)
& (Join-Path $jdk 'bin\javac.exe') -encoding UTF-8 -source 17 -target 17 -classpath $androidJar -d (Join-Path $build 'classes') @sources
if($LASTEXITCODE){throw 'javac failed'}
& (Join-Path $jdk 'bin\jar.exe') cf (Join-Path $build 'classes.jar') -C (Join-Path $build 'classes') .
& (Join-Path $bt 'd8.bat') --lib $androidJar --min-api 26 --output (Join-Path $build 'dex') (Join-Path $build 'classes.jar')
if($LASTEXITCODE){throw 'd8 failed'}
Push-Location (Join-Path $build 'dex'); & (Join-Path $bt 'aapt.exe') add (Join-Path $build 'unsigned.apk') 'classes.dex' | Out-Null; Pop-Location
$aligned=Join-Path $build 'aligned.apk'; & (Join-Path $bt 'zipalign.exe') -f -p 4 (Join-Path $build 'unsigned.apk') $aligned
$key=Join-Path $tool 'aniworld-release.jks'; $final=Join-Path $out ("NSN-Neo-"+$(if($Flavor -eq 'tv'){'FireTV'}else{'Android'})+"-preview.apk")
& (Join-Path $bt 'apksigner.bat') sign --ks $key --ks-pass 'pass:AniWorldLocal2026!' --key-pass 'pass:AniWorldLocal2026!' --ks-key-alias aniworld --out $final $aligned
& (Join-Path $bt 'apksigner.bat') verify --verbose $final
Get-Item $final | Select-Object FullName,Length,LastWriteTime
