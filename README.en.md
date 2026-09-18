# YSM Ragdoll

[简体中文](README.md) | **English**

A Minecraft ragdoll physics mod for Yes Steve Model (YSM). On player death, the
client captures the loaded YSM mesh, textures and bone pose, then simulates an
independent ragdoll with JBullet. Features include block collisions, joint limits,
player pushing, explosion impulses, buoyancy and stick-based grabbing.

## Downloads And Compatibility

The downloads below are for the **0.5.26-beta.1** three-platform preview.

Download the **`-all.jar`** from
[GitHub Releases](https://github.com/Esnowflake/YSMRagdoll/releases).
Place the mod JAR in the matching game instance's `mods` folder; do not double-click it.
Do not use the plain JAR without bundled dependencies or place Source code archives in `mods`.

| Minecraft | Loader | Java | Status | Download |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.x | 17 | Beta | [Download JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-forge-all.jar) |
| 1.20.1 | Fabric | 17 | Beta | [Download JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-fabric-all.jar) |
| 1.21.1 | Fabric | 21 | Beta | [Download JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.21.1-fabric-all.jar) |

## Getting Started

Choose the JAR for your Minecraft version and loader, close the game, and place
it in that instance's `mods` folder alongside the matching **YSM 2.6.5** JAR.
Fabric instances also need **Fabric API**. When updating, remove the old
YSM Ragdoll JAR to avoid loading two versions.

Once you have selected a YSM model in-game, player deaths can produce ragdolls.
To adjust the effect or try it without dying, assign a **YSM Ragdoll settings**
hotkey under Options → Controls → Key Binds; it is unbound by default.
The Advanced page lets you spawn a ragdoll from your current model.

To pick up and drag a ragdoll, enable grabbing on the Testing page and hold
a stick in your main hand. Aim at the ragdoll, hold right-click to grab,
release to let go, and scroll to adjust the distance.

### Multiplayer

The server also needs this mod to synchronize player deaths. Clients without
the mod can still join, but will not display ragdolls. With the mod only on
your client, you can still spawn ragdolls manually through the settings page.

Ragdolls are simulated on the client; they are not server entities and are
not saved with the world.

## More Information

- [Changelog](CHANGELOG.md)
- [Troubleshooting and technical guide](docs/GUIDE.zh-CN.md)

## Reporting Issues

Open an [issue](https://github.com/Esnowflake/YSMRagdoll/issues) with versions,
reproduction steps and relevant logs. Remove credentials and private information
before uploading.

## Development And Releases

See [Contributing](CONTRIBUTING.md) and [Building](docs/BUILDING.md).
Changes are tracked in the [changelog](CHANGELOG.md). GitHub Actions builds
the mod JARs, which are published to Releases after validation.

## License

[MIT](LICENSE). Extracted OpenYSM parser classes retain their
[MIT license](licenses/OpenYSM-LICENSE.txt). Mod JARs include JBullet and vecmath.
Minecraft, the complete YSM mod and player model resources are not bundled.
Player resources remain subject to their authors' licenses.
