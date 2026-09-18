# YSM Ragdoll

[简体中文](README.md) | **English**

A Minecraft ragdoll physics mod for Yes Steve Model (YSM). On player death, the
client captures the loaded YSM mesh, textures and bone pose, then simulates an
independent ragdoll with JBullet. Features include block collisions, joint limits,
player pushing, explosion impulses, buoyancy and stick-based grabbing.

## Downloads And Compatibility

Download the **`-all.jar`** from
[GitHub Releases](https://github.com/Esnowflake/YSMRagdoll/releases).
The plain JAR and source archives are not complete installation packages.

| Minecraft | Loader | Java | Status | Release Download |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.x | 17 | Implemented; built against Forge 47.4.10 | [0.5.26-beta.1 JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-forge-all.jar) |
| 1.20.1 | Fabric | 17 | Experimental port; separate build, tests and YSM injection checks | [0.5.26-beta.1 JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-fabric-all.jar) |
| 1.21.1 | Fabric | 21 | Experimental port; separate build, tests and YSM injection checks | [0.5.26-beta.1 JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.21.1-fabric-all.jar) |

## Installation And Use

1. Close Minecraft and install the matching mod package and YSM 2.6.5 in your
   instance's `mods` directory. Fabric also requires Fabric API.
2. Configure your own YSM model resources. Keep only one YSM Ragdoll version.
3. Assign the settings hotkey in Minecraft's controls; it is unbound by default.
4. Death creates a ragdoll in single-player. Advanced settings can also create
   local test ragdolls.
5. Enable grabbing in the Testing category, hold a stick in the main hand,
   hold right-click to grab, release to let go, and scroll to adjust distance.

For multiplayer death snapshots, the server must also install this mod.
Clients without it can still join but cannot see ragdolls. Client-only
installations can create local test ragdolls manually.
Physics is a client-side visual effect, not a server-authoritative entity, and
ragdolls are not saved across worlds.

## Local Build

Run the build tools with **JDK 21**; 1.20.1 also needs a **JDK 17** toolchain.
On Windows, use PowerShell 5.1 or 7:

```powershell
.\build.ps1 clean build
.\build.ps1 --project-dir fabric --project-prop target_mc=1.20.1 clean build
.\build.ps1 --project-dir fabric --project-prop target_mc=1.21.1 clean build
```

The script stores Gradle distributions and dependencies in `.gradle-home/`,
and temporary files in `.tmp/`, under the project directory. A project on D:
keeps these new downloads off C:. Explicit `GRADLE_USER_HOME` and
`GRADLE_TEMP_DIR` values take precedence; choose non-system-drive locations.
Configure the IDE's Gradle user home separately. Existing C: caches are not deleted.

An explicit `JAVA_HOME` is respected. If unset and both a project-local JDK 17
in `.jdks/` and a system JDK 21 exist, the script uses 21 to run Gradle and 17 to
compile, working around this Windows host's Java 17 loopback issue.
The script does not install a JDK.

Proxy environment variables take precedence over the Windows system HTTP/HTTPS
proxy. For PAC or SOCKS configurations, supply an HTTP proxy endpoint:

```powershell
$env:GRADLE_PROXY_URL = 'http://127.0.0.1:7890'
.\build.ps1 clean build
```

See [Building](docs/BUILDING.md) for the pinned OpenYSM dependency, checksum,
Linux/macOS commands and IDE setup. Forge outputs are in `build/libs/`;
Fabric outputs are in `fabric/build/<Minecraft version>/libs/`. Distribute
the dependency-inclusive JAR only.

## Development And Releases

- [Changelog](CHANGELOG.md): unreleased work and historical versions; new release notes are generated from it.
- [Releasing](RELEASING.md): tags, version checks, GitHub builds and checksums.
- [Contributing](CONTRIBUTING.md): syncing, branches, pull requests and conflicts.
- [Detailed Chinese guide](docs/GUIDE.zh-CN.md): physics, coordinate contracts, settings and troubleshooting.
- [Historical rollback notes](ROLLBACK.md): old development records; local paths are historical only.

CI checks pull requests and the main branch. Tags `vMAJOR.MINOR.PATCH`, optionally
with `-beta.N` / `-rc.N` suffixes, trigger GitHub builds and draft releases for
maintainer review. Historical tags without `v` remain unchanged.
Release packages come from GitHub builds, never from a developer's cache.

## License

[MIT](LICENSE). Extracted OpenYSM parser classes retain their
[MIT license](licenses/OpenYSM-LICENSE.txt). Packages include JBullet and vecmath.
Minecraft, the complete YSM mod and player model resources are not bundled.
Player resources remain subject to their authors' licenses.
