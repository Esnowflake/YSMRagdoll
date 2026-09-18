[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs = @('clean', 'build')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$savedEnvironment = @{}
foreach ($name in @('GRADLE_USER_HOME', 'GRADLE_TEMP_DIR', 'TEMP', 'TMP', 'JAVA_HOME', 'GRADLE_OPTS')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name)
}
Push-Location $projectRoot
try {

if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-home'
}
if (-not $env:GRADLE_TEMP_DIR) {
    $env:GRADLE_TEMP_DIR = Join-Path $projectRoot '.tmp'
}
New-Item -ItemType Directory -Force -Path $env:GRADLE_TEMP_DIR | Out-Null
$env:TEMP = $env:GRADLE_TEMP_DIR
$env:TMP = $env:GRADLE_TEMP_DIR

$bundledJava17 = Get-ChildItem (Join-Path $projectRoot '.jdks') -Directory -ErrorAction SilentlyContinue |
    Where-Object {
        $_.Name -match 'jdk-17' -and (Test-Path (Join-Path $_.FullName 'bin\java.exe'))
    } |
    Select-Object -First 1
$systemJava21 = Get-ChildItem @(
    (Join-Path ${env:ProgramFiles} 'Microsoft'),
    (Join-Path ${env:ProgramFiles} 'Java')
) -Directory -ErrorAction SilentlyContinue |
    Where-Object {
        $_.Name -match 'jdk-21' -and (Test-Path (Join-Path $_.FullName 'bin\java.exe'))
    } |
    Select-Object -First 1

if (-not $env:JAVA_HOME -and $systemJava21 -and $bundledJava17) {
    # This Windows host needs Java 21 for Gradle; compilation still uses Java 17.
    $env:JAVA_HOME = $systemJava21.FullName
} elseif (-not $env:JAVA_HOME -and $bundledJava17) {
    $env:JAVA_HOME = $bundledJava17.FullName
}

if ($bundledJava17 -and
    -not ($GradleArgs -match 'org\.gradle\.java\.installations\.paths')) {
    # Use the long option form because PowerShell/cmd can reinterpret Gradle's -P prefix.
    $GradleArgs = @(
        '--project-prop',
        "org.gradle.java.installations.paths=$($bundledJava17.FullName)"
    ) + $GradleArgs
}

function Get-FirstEnvironmentValue {
    param([string[]] $Names)

    foreach ($name in $Names) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value
        }
    }
    return $null
}

function Add-ProxyOptions {
    param(
        [string] $Prefix,
        [string] $RawProxy,
        [System.Collections.Generic.List[string]] $Options
    )

    if ([string]::IsNullOrWhiteSpace($RawProxy)) {
        return
    }

    if ($RawProxy -notmatch '^[a-zA-Z][a-zA-Z0-9+.-]*://') {
        $RawProxy = "http://$RawProxy"
    }

    $proxy = [Uri]$RawProxy
    if (-not $proxy.Host -or $proxy.Scheme -ne 'http') {
        throw 'Use an HTTP proxy URL (including for HTTPS destinations).'
    }

    $Options.Add("-D${Prefix}.proxyHost=$($proxy.Host)")
    if ($proxy.Port -gt 0) {
        $Options.Add("-D${Prefix}.proxyPort=$($proxy.Port)")
    }
    if ($proxy.UserInfo) {
        $userInfo = $proxy.UserInfo.Split(':', 2)
        $user = [Uri]::UnescapeDataString($userInfo[0])
        $password = ''
        if ($userInfo.Count -eq 2) {
            $password = [Uri]::UnescapeDataString($userInfo[1])
        }
        # The wrapper expands GRADLE_OPTS through cmd.exe, not an argument array.
        if ($user -match '[\s"&|<>^%!()]' -or $password -match '[\s"&|<>^%!()]') {
            throw 'Proxy credentials contain characters unsafe for the Windows wrapper.'
        }
        $Options.Add("-D${Prefix}.proxyUser=$user")
        $Options.Add("-D${Prefix}.proxyPassword=$password")
    }
}

$proxyOptions = [System.Collections.Generic.List[string]]::new()
$httpsProxy = Get-FirstEnvironmentValue @('GRADLE_PROXY_HTTPS', 'HTTPS_PROXY', 'https_proxy')
$httpProxy = Get-FirstEnvironmentValue @('GRADLE_PROXY_HTTP', 'HTTP_PROXY', 'http_proxy')
$allProxy = Get-FirstEnvironmentValue @('GRADLE_PROXY_URL', 'ALL_PROXY', 'all_proxy')
$combinedProxy = Get-FirstEnvironmentValue @('GRADLE_PROXY_URL')

if (-not ($combinedProxy -or $allProxy -or $httpProxy -or $httpsProxy)) {
    $settings = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
    if ($settings.ProxyEnable -eq 1 -and $settings.ProxyServer) {
        if ($settings.ProxyServer -match '=') {
            foreach ($entry in $settings.ProxyServer.Split(';')) {
                $parts = $entry.Trim().Split('=', 2)
                if ($parts.Count -eq 2) {
                    if ($parts[0] -eq 'http') { $httpProxy = $parts[1] }
                    if ($parts[0] -eq 'https') { $httpsProxy = $parts[1] }
                }
            }
            if (-not ($httpProxy -or $httpsProxy)) {
                throw 'The system proxy has no HTTP/HTTPS endpoint. Set GRADLE_PROXY_URL.'
            }
        } else {
            $combinedProxy = $settings.ProxyServer
        }
        Write-Host 'Using Windows system proxy.'
    } elseif ($settings.AutoConfigURL) {
        throw 'Proxy auto-configuration (PAC) requires an explicit GRADLE_PROXY_URL.'
    }
}

if ($combinedProxy -or $allProxy) {
    if (-not $combinedProxy) {
        $combinedProxy = $allProxy
    }
    Add-ProxyOptions -Prefix 'http' -RawProxy $combinedProxy -Options $proxyOptions
    Add-ProxyOptions -Prefix 'https' -RawProxy $combinedProxy -Options $proxyOptions
} else {
    if (-not $httpsProxy) { $httpsProxy = $httpProxy }
    if (-not $httpProxy) { $httpProxy = $httpsProxy }
    Add-ProxyOptions -Prefix 'http' -RawProxy $httpProxy -Options $proxyOptions
    Add-ProxyOptions -Prefix 'https' -RawProxy $httpsProxy -Options $proxyOptions
}

$noProxy = Get-FirstEnvironmentValue @('GRADLE_PROXY_NO_PROXY', 'NO_PROXY', 'no_proxy')
if (-not $noProxy) { $noProxy = 'localhost,127.0.0.1' }
if ($noProxy) {
    $hosts = $noProxy.Split(',') |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ } |
        ForEach-Object { $_ -replace '^\*://', '' -replace '/.*$', '' }
    if ($hosts) {
        if ($hosts -match '[^a-zA-Z0-9.*:\-\[\]]') {
            throw 'NO_PROXY must contain comma-separated host names or IP addresses.'
        }
        # GRADLE_OPTS is expanded by cmd.exe in gradlew.bat, so escape its pipe separators.
        $nonProxyHosts = ($hosts -replace '^\.', '*.') -join '^|'
        $proxyOptions.Add("-Dhttp.nonProxyHosts=$nonProxyHosts")
        $proxyOptions.Add("-Dhttps.nonProxyHosts=$nonProxyHosts")
    }
}

$existingGradleOpts = $env:GRADLE_OPTS
if ($proxyOptions.Count -gt 0) {
    $env:GRADLE_OPTS = (($proxyOptions + @($existingGradleOpts)) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ' '
}

if ($proxyOptions.Count -gt 0) {
    Write-Host "Gradle proxy enabled; proxy credentials are not printed."
} else {
    Write-Host "Gradle proxy not configured; using direct network access."
}
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
if ($env:JAVA_HOME) {
    Write-Host "JAVA_HOME=$env:JAVA_HOME"
}

& (Join-Path $projectRoot 'gradlew.bat') @GradleArgs
$buildExitCode = $LASTEXITCODE
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        if ($null -eq $savedEnvironment[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
        }
    }
    Pop-Location
}
exit $buildExitCode
