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

| Minecraft | Loader | Java | Status | Download |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.x | 17 | Beta | [Download JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-forge-all.jar) |
| 1.20.1 | Fabric | 17 | Beta | [Download JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-fabric-all.jar) |
| 1.21.1 | Fabric | 21 | Beta | [Download JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.21.1-fabric-all.jar) |

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

## More Information

- [Changelog](CHANGELOG.md)
- [Troubleshooting and technical guide](docs/GUIDE.zh-CN.md)

## License

[MIT](LICENSE). Extracted OpenYSM parser classes retain their
[MIT license](licenses/OpenYSM-LICENSE.txt). Packages include JBullet and vecmath.
Minecraft, the complete YSM mod and player model resources are not bundled.
Player resources remain subject to their authors' licenses.
