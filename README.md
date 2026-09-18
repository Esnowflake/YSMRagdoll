# YSM Ragdoll

**简体中文** | [English](README.en.md)

面向 Yes Steve Model（YSM）的 Minecraft 布娃娃物理模组。玩家死亡后，
客户端冻结当前 YSM 模型的网格、纹理与骨骼姿态，使用 JBullet 模拟独立布娃娃。
支持方块碰撞、关节限制、玩家推动、爆炸冲击、液体浮力及木棍牵引。

## 下载与兼容性

从 [GitHub Releases](https://github.com/Esnowflake/YSMRagdoll/releases) 下载带
`-all.jar` 后缀的安装包。普通 JAR 和 Source code 压缩包不是完整安装包。

| Minecraft | 平台 | Java | 状态 | 下载 |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.x | 17 | Beta | [下载 JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-forge-all.jar) |
| 1.20.1 | Fabric | 17 | Beta | [下载 JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-fabric-all.jar) |
| 1.21.1 | Fabric | 21 | Beta | [下载 JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.21.1-fabric-all.jar) |

## 安装与使用

1. 退出游戏，在匹配版本、加载器的实例 `mods` 中安装本模组及 YSM 2.6.5；Fabric 还需 Fabric API。
2. 配置自己的 YSM 模型资源；更新时移除旧版 YSM Ragdoll，只保留一份。
3. 在游戏按键设置中绑定本模组的设置快捷键（默认未绑定）。
4. 单人游戏死亡后会生成布娃娃；高级设置可手动创建本地测试布娃娃。
5. 开启“测试 → 牵引模式”，主手拿木棍，按住右键抓取，松开释放，滚轮调距。

多人游戏需要服务器安装本模组才能广播死亡快照。没有安装的客户端仍可加入，
但看不到布娃娃；只有客户端安装时仍可手动创建本地测试布娃娃。
物理是客户端视觉效果，不是服务器权威实体，也不跨世界保存。

## 更多信息

- [更新日志](CHANGELOG.md)
- [故障排查与技术说明](docs/GUIDE.zh-CN.md)

## 许可

本项目采用 [MIT](LICENSE)；提取的 OpenYSM 解析类保留其
[MIT 许可](licenses/OpenYSM-LICENSE.txt)。安装包包含 JBullet 和 vecmath 依赖。
不包含 Minecraft、完整 YSM 模组或玩家模型资源；玩家资源遵循各自作者的许可。
