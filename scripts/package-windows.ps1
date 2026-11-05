# Packages Amidst as a portable Windows app: AMIDST.exe with its own Java runtime, zipped.
# Run after "mvn package". Needs JDK 25 or newer for jpackage, since Minecraft 26.1 and later run on Java 25.
# The app still loads the game from the Minecraft installation of the launcher; it does not ship Minecraft.
param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Out = "target\package",
    # File name of the zip, without .zip
    [string]$Name
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$jar = Get-ChildItem "$root\target\amidst-v*.jar" | Select-Object -First 1
if (-not $jar) { throw "No Amidst jar in target, run mvn package first" }
$version = (Get-Content "$root\src\main\resources\amidst\metadata.properties" |
    Where-Object { $_ -match "^amidst\.version\.(major|minor|patch)=" } |
    ForEach-Object { $_.Split("=")[1] }) -join "."
if (-not $Name) { $Name = "AMIDST-$version-windows" }

$outDir = Join-Path $root $Out
$stage = Join-Path $outDir "stage"
Remove-Item -Recurse -Force $outDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item $jar.FullName $stage

# Minecraft itself runs inside this runtime, so it gets every module of the JDK rather than a trimmed set.
# jlink refuses to put itself into an image, so the two tooling modules that need it stay out.
$modules = (& "$Jdk\bin\java.exe" --list-modules |
    ForEach-Object { $_.Split("@")[0] } |
    Where-Object { $_ -notin "jdk.jlink", "jdk.jpackage" }) -join ","
& "$Jdk\bin\jpackage.exe" --type app-image --name AMIDST --app-version $version `
    --input $stage --main-jar $jar.Name `
    --icon "$root\src\main\resources\amidst\icon\amidst.ico" `
    --add-modules $modules `
    --dest $outDir | Out-Host
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

$zip = Join-Path $outDir "$Name.zip"
Compress-Archive -Path "$outDir\AMIDST" -DestinationPath $zip
Write-Output $zip
