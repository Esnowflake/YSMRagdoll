# YSM Ragdoll

**简体中文** | [English](README.en.md)

面向 Yes Steve Model（YSM）的 Minecraft 布娃娃物理模组。玩家死亡后，
客户端冻结当前 YSM 模型的网格、纹理与骨骼姿态，使用 JBullet 模拟独立布娃娃。
支持方块碰撞、关节限制、玩家推动、爆炸冲击、液体浮力及木棍牵引。

## 下载与兼容性

以下下载对应 **0.5.26-beta.1** 三平台测试版。

从 [GitHub Releases](https://github.com/Esnowflake/YSMRagdoll/releases) 下载带
`-all.jar` 后缀的模组文件，放入对应游戏实例的 `mods` 文件夹即可，无需双击运行。
不要使用普通不带依赖的 JAR，也不要将 Source code 源码压缩包放入 `mods`。

| Minecraft | 平台 | Java | 状态 | 下载 |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.x | 17 | Beta | [下载 JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-forge-all.jar) |
| 1.20.1 | Fabric | 17 | Beta | [下载 JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-fabric-all.jar) |
| 1.21.1 | Fabric | 21 | Beta | [下载 JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.21.1-fabric-all.jar) |

## 开始使用

选择与你的 Minecraft 版本和加载器对应的 JAR，退出游戏后放入该实例的
`mods` 文件夹。还需要对应版本的 **YSM 2.6.5**；使用 Fabric 时，请一并加入
**Fabric API**。更新本模组时，记得移除旧版 JAR，避免重复加载。

进入游戏并选好 YSM 模型后，就可以体验死亡后的布娃娃效果。
想调整参数或先试试效果，可以在游戏的“选项 → 控制 → 按键绑定”中
为 **YSM Ragdoll 设置**指定快捷键（默认未绑定），再打开设置页面。
“高级”页面支持手动生成当前模型的布娃娃，无需先让角色死亡。

想抓取、拖动布娃娃，可以在“测试”页面开启牵引模式，然后主手拿着木棍，
瞄准布娃娃并按住右键；松开右键即可释放，滚轮可以调整抓取距离。

### 多人游戏

要在联机时看到玩家死亡后生成的布娃娃，服务器也需要加入本模组，
用于同步死亡信息。未加入本模组的客户端仍可连接服务器，但不会显示布娃娃。
如果只有你的客户端加入了本模组，仍可通过设置页面手动生成布娃娃体验效果。

布娃娃仅在客户端模拟，不会作为实体保存在服务器或存档中。

## 更多信息

- [更新日志](CHANGELOG.md)
- [故障排查与技术说明](docs/GUIDE.zh-CN.md)

## 问题反馈

请通过 [Issues](https://github.com/Esnowflake/YSMRagdoll/issues) 提供版本信息、
复现步骤和相关日志。上传前请移除账号凭据、私人服务器地址等信息。

## 开发与发布

参与开发请阅读 [贡献指南](CONTRIBUTING.md) 和 [构建文档](docs/BUILDING.md)。
版本更新统一记录在 [CHANGELOG](CHANGELOG.md)；模组 JAR 由 GitHub Actions
构建，通过检查后发布至 Releases。

## 许可

本项目采用 [MIT](LICENSE)；提取的 OpenYSM 解析类保留其
[MIT 许可](licenses/OpenYSM-LICENSE.txt)。模组 JAR 包含 JBullet 和 vecmath 依赖。
不包含 Minecraft、完整 YSM 模组或玩家模型资源；玩家资源遵循各自作者的许可。
