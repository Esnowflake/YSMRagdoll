# YSM Ragdoll

**简体中文** | [English](README.en.md)

让 YSM 玩家模型在死亡后成为可碰撞、可推动、可牵引的物理布娃娃。

YSM Ragdoll 是面向 Yes Steve Model（YSM）的 Minecraft 模组。它保留死亡瞬间的模型与姿态，
在客户端模拟关节、地形碰撞、爆炸冲击和液体浮力，也支持手动创建测试布娃娃。

[下载安装](https://github.com/Esnowflake/YSMRagdoll/releases) · [使用指南](docs/GUIDE.zh-CN.md) · [更新日志](CHANGELOG.md) · [反馈问题](https://github.com/Esnowflake/YSMRagdoll/issues)

## 下载与兼容性

**Forge 1.20.1 玩家可选择正式版 0.5.26；Fabric 玩家请选择对应 Minecraft 版本的 Beta 包。**
三平台测试版为 `0.5.26-beta.1`，Fabric 完整游戏内验收尚未完成。

| 版本 | Minecraft | 加载器 | Java | 下载 |
| --- | --- | --- | --- | --- |
| **0.5.26 正式版** | 1.20.1 | Forge 47.4.x | 17 | [Forge 安装包](https://github.com/Esnowflake/YSMRagdoll/releases/download/0.5.26/ysmragdoll-0.5.26-all.jar) |
| 0.5.26-beta.1 测试版 | 1.20.1 | Forge 47.4.x | 17 | [Forge Beta](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-forge-all.jar) |
| 0.5.26-beta.1 测试版 | 1.20.1 | Fabric Loader ≥ 0.16.14 | 17 | [Fabric 1.20.1 Beta](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-fabric-all.jar) |
| 0.5.26-beta.1 测试版 | 1.21.1 | Fabric Loader ≥ 0.16.14 | 21 | [Fabric 1.21.1 Beta](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.21.1-fabric-all.jar) |

客户端需安装与游戏版本、加载器匹配的 **YSM 2.6.5**，Fabric 还需 **Fabric API**。
目前没有 Forge 1.21.1 或 NeoForge 安装包。

请下载 **`-all.jar`**，放入 `mods` 文件夹，无需双击运行。普通 JAR 和 GitHub 自动提供的 Source code 压缩包不能作为完整模组安装包。
[测试版发行说明与 SHA-256 校验文件](https://github.com/Esnowflake/YSMRagdoll/releases/tag/v0.5.26-beta.1)可用于核对下载内容。

## 安装与开始使用

1. 退出游戏，将对应安装包和所需依赖放入游戏实例的 `mods` 文件夹；更新时移除旧版 YSM Ragdoll。
2. 启动游戏，确认你的 YSM 模型已正常加载。
3. 在 Minecraft「选项 → 控制 → 按键绑定」中绑定“打开YSM布娃娃设置”（默认未绑定）。
4. 单人游戏中死亡后会生成布娃娃；也可在“高级”设置点击“创建一个布娃娃”进行本地测试。
5. 在“测试”分类开启“牵引模式”，主手拿木棍，瞄准布娃娃按住右键牵引，松开释放，滚轮调整距离。

默认最多保留 **2** 具布娃娃，每具存在 **20 秒**。数量、寿命、物理参数与清理方式均可在设置中调整。

## 多人游戏与使用限制

服务器需要安装匹配版本的本模组，才能广播玩家死亡信息。客户端安装本模组和 YSM 后，
使用自己已加载的模型生成布娃娃。未安装本模组的客户端仍可加入，但看不到布娃娃；
仅客户端安装时，可以手动创建本地测试布娃娃。

物理模拟在各客户端独立运行，姿态可能不同。布娃娃属于视觉效果，不参与服务器伤害或实体逻辑，
也不跨世界保存。特殊模型骨骼或 YSM 版本变化可能影响捕获效果，详见[使用指南](docs/GUIDE.zh-CN.md)。

## 文档与贡献

- [使用与故障排查](docs/GUIDE.zh-CN.md)：设置、牵引、日志和常见问题。
- [开发与构建](docs/BUILDING.md)：开发环境、三平台构建及测试。
- [贡献指南](CONTRIBUTING.md)：问题反馈、分支与 Pull Request。
- [发布流程](docs/RELEASING.md)：版本、标签、构建校验与发行草稿。
- [文档索引](docs/README.md)：技术说明与其他参考资料。

## 许可证与致谢

本项目采用 [MIT 许可证](LICENSE)。模型解析使用 OpenYSM 的部分代码，保留其
[MIT 许可](licenses/OpenYSM-LICENSE.txt)；物理模拟使用 JBullet 和 vecmath。

发行包不包含 Minecraft、完整 YSM 模组或玩家模型、纹理与动画资源。玩家资源遵循各自作者的许可。
