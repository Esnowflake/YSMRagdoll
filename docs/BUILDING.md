# Building / 本地构建

[中文](../README.md) | [English](../README.en.md)

## Requirements / 前置条件

- Run Gradle with JDK 21; both Minecraft 1.20.1 targets also need a JDK 17 toolchain.
- The wrapper downloads Gradle 8.14.3 and checks its pinned SHA-256.
- OpenYSM is compile-only; selected parser classes are extracted with their MIT license.
- Internet access is required on the first build. Use your system proxy where needed.

## OpenYSM Dependency / 编译依赖

Run from the repository root in PowerShell. The URL and checksum are pinned;
the JAR is saved under `libs/` on the project drive, never committed.

```powershell
New-Item -ItemType Directory -Force libs | Out-Null
$url = 'https://github.com/OpenYSM/OpenYSM/releases/download/ysm-2.6.5-forge%2Bmc1.20.1/ysm-2.6.5-forge%2Bmc1.20.1-all.jar'
$file = 'libs/openysm-2.6.5-forge+mc1.20.1.jar'
Invoke-WebRequest $url -Proxy 'http://<proxy-host>:<proxy-port>' -OutFile $file
$expected = '46eac038c314e7df1af80deea3c9e507f9f1e7ddf66b04eec455560dbfd51909'
if ((Get-FileHash $file -Algorithm SHA256).Hash.ToLower() -ne $expected) {
    throw 'OpenYSM checksum mismatch'
}
.\build.ps1 clean build
```

Replace the example proxy with your system proxy; omit `-Proxy` only when direct
access is intended. CI downloads and verifies this dependency automatically.

## Windows

`build.ps1` supports Windows PowerShell 5.1 and PowerShell 7.
Default cache and temporary directories are `.gradle-home/` and `.tmp/`,
ignored by Git. Existing environment overrides are respected.

Proxy environment variables take precedence over the Windows system proxy.
PAC requires an explicit `GRADLE_PROXY_URL`; SOCKS endpoints are not treated as
HTTP proxies. Shell-sensitive credentials are rejected. Environment variables
and the working directory are restored afterward.

An explicit `JAVA_HOME` chooses the Gradle JVM. Use JDK 21 for Gradle and make
JDK 17 available as a toolchain for the 1.20.1 targets. Minecraft 1.21.1 compiles
with JDK 21. The script does not install JDKs.

## Linux / macOS

Use JDK 21 with a JDK 17 toolchain available and keep downloads on the project drive:

```sh
export GRADLE_USER_HOME="$PWD/.gradle-home"
export TMPDIR="$PWD/.tmp"
mkdir -p "$TMPDIR" libs
curl --fail --location --retry 3 \
  'https://github.com/OpenYSM/OpenYSM/releases/download/ysm-2.6.5-forge%2Bmc1.20.1/ysm-2.6.5-forge%2Bmc1.20.1-all.jar' \
  -o libs/openysm-2.6.5-forge+mc1.20.1.jar
echo '46eac038c314e7df1af80deea3c9e507f9f1e7ddf66b04eec455560dbfd51909  libs/openysm-2.6.5-forge+mc1.20.1.jar' | sha256sum --check
bash ./gradlew --no-daemon clean build
```

On macOS use `shasum -a 256 -c` instead of `sha256sum --check`.
Java proxy settings go in `GRADLE_OPTS`: `http.proxyHost`, `http.proxyPort`,
`https.proxyHost`, and `https.proxyPort`. Shell `HTTPS_PROXY` alone does not
configure Java's dependency resolver.

## IDE

Open the Gradle project and use its wrapper. Set **Gradle user home** to an
absolute project-drive path such as `<project-directory>/.gradle-home`.
IDE calls do not necessarily invoke `build.ps1` or `gradlew.bat`; wrapper defaults
alone do not configure IDE caches.

## Verification

Use JDK 21 to run Gradle 8.14.3 / Loom 1.11.8, with a JDK 17 toolchain
also available for both 1.20.1 targets. There is no Forge 1.21.1 target.

```powershell
.\build.ps1 clean build
.\build.ps1 --project-dir fabric --project-prop target_mc=1.20.1 clean build
.\build.ps1 --project-dir fabric --project-prop target_mc=1.21.1 clean build
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:TEMP = "$PWD\.tmp"
$env:TMP = $env:TEMP
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/release.py validate v0.5.26-beta.1
python scripts/release.py prepare v0.5.26-beta.1
```

Python 3.11+ is required only for release tooling. Use the current project version
instead of the example tag after a bump. Gradle reports:
`build/reports/tests/test/index.html`; release-ready files: `build/release/`.
Runtime validation still requires a compatible game instance and YSM models.

Fabric reports are in `fabric/build/<version>/reports/tests/test/`.
For the same YSM injection checks used in CI, download the official test-only
binary using `bash scripts/download-ysm.sh fabric-1.20.1` (or `fabric-1.21.1`)
with `HTTPS_PROXY` set to your system HTTP proxy. Then add
`--project-prop ysm_smoke=true` to the Fabric build command. These checks load
transformed YSM and vanilla classes without opening a game window; they do not
test world rendering, death snapshots, model selection, or multiplayer behavior.
Official YSM binaries stay in ignored `libs/compatibility/` and are never shipped.

Release tooling validates each platform with `prepare <tag> --target <target>`.
CI collects each verified bundle separately; `assemble <tag>` requires all three
under `build/bundles/<target>/` before writing the combined release directory.
