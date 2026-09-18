# YSM Ragdoll

[简体中文](README.md) | **English**

Turn YSM player models into physical ragdolls that collide, react and can be grabbed after death.

YSM Ragdoll is a Minecraft mod for Yes Steve Model (YSM). It captures the model and pose at death,
then simulates joints, terrain collisions, explosion impulses and buoyancy on the client.
You can also create local test ragdolls from the settings screen.

[Downloads](https://github.com/Esnowflake/YSMRagdoll/releases) · [User guide (中文)](docs/GUIDE.zh-CN.md) · [Changelog](CHANGELOG.md) · [Report an issue](https://github.com/Esnowflake/YSMRagdoll/issues)

## Downloads and compatibility

**Forge 1.20.1 players can use stable 0.5.26. Fabric players should select the Beta package for their Minecraft version.**
The three-platform preview is `0.5.26-beta.1`; full in-game acceptance testing for Fabric is still pending.

| Version | Minecraft | Loader | Java | Download |
| --- | --- | --- | --- | --- |
| **0.5.26 Stable** | 1.20.1 | Forge 47.4.x | 17 | [Forge package](https://github.com/Esnowflake/YSMRagdoll/releases/download/0.5.26/ysmragdoll-0.5.26-all.jar) |
| 0.5.26-beta.1 Beta | 1.20.1 | Forge 47.4.x | 17 | [Forge Beta](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-forge-all.jar) |
| 0.5.26-beta.1 Beta | 1.20.1 | Fabric Loader ≥ 0.16.14 | 17 | [Fabric 1.20.1 Beta](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-fabric-all.jar) |
| 0.5.26-beta.1 Beta | 1.21.1 | Fabric Loader ≥ 0.16.14 | 21 | [Fabric 1.21.1 Beta](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.21.1-fabric-all.jar) |

Clients need **YSM 2.6.5** for the matching Minecraft version and loader. Fabric also requires **Fabric API**.
There are no Forge 1.21.1 or NeoForge packages.

Download **`-all.jar`** and put it in `mods`; do not double-click it. Plain JARs and GitHub's Source code archives are not complete mod packages.
See the [Beta release notes and SHA-256 checksums](https://github.com/Esnowflake/YSMRagdoll/releases/tag/v0.5.26-beta.1) to verify your download.

## Installation and first use

1. Close Minecraft. Put the matching package and required dependencies in your instance's `mods` folder. Remove older YSM Ragdoll versions.
2. Start the game and confirm your YSM model loads correctly.
3. Assign the YSM Ragdoll settings key in Minecraft's controls; it is unbound by default.
4. Death creates a ragdoll in single-player. You can also create a local test ragdoll in the Advanced settings.
5. Enable grabbing in the Testing category, hold a stick in your main hand, aim at a ragdoll and hold right-click. Release to let go; scroll to adjust distance.

By default, up to **2** ragdolls remain for **20 seconds** each. Settings control their count, lifetime, physics and removal.

## Multiplayer and limitations

The server needs a matching version of this mod to broadcast deaths. Clients with this mod and YSM
create ragdolls from their locally loaded models. Clients without the mod can still join but do not see
ragdolls. Client-only installations can create local test ragdolls manually.

Each client simulates physics independently, so poses may differ. Ragdolls are visual effects, do not participate
in server damage or entity logic, and are not saved across worlds. Unusual skeletons or YSM version changes
may affect model capture. See the [user guide (中文)](docs/GUIDE.zh-CN.md).

## Documentation and contributing

- [User guide (中文)](docs/GUIDE.zh-CN.md): settings, grabbing, logs and troubleshooting.
- [Development and builds](docs/BUILDING.md): environment, three-platform builds and tests.
- [Contributing](CONTRIBUTING.md): bug reports, branches and pull requests.
- [Releasing](docs/RELEASING.md): versions, tags, validation and release drafts.
- [Documentation index](docs/README.md): technical references and other documentation.

## License and acknowledgements

This project is licensed under [MIT](LICENSE). Selected OpenYSM model parser code retains its
[MIT license](licenses/OpenYSM-LICENSE.txt). Physics uses JBullet and vecmath.

Packages do not include Minecraft, the complete YSM mod, or player models, textures and animations.
Player resources remain subject to their authors' licenses.
