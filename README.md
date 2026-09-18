# YSM Ragdoll

**简体中文** | [English](README.en.md)

面向 Yes Steve Model（YSM）的 Minecraft 布娃娃物理模组。玩家死亡后，
客户端冻结当前 YSM 模型的网格、纹理与骨骼姿态，使用 JBullet 模拟独立布娃娃。
支持方块碰撞、关节限制、玩家推动、爆炸冲击、液体浮力及木棍牵引。

## 下载与兼容性

从 [GitHub Releases](https://github.com/Esnowflake/YSMRagdoll/releases) 下载带
`-all.jar` 后缀的安装包。普通 JAR 和 Source code 压缩包不是完整安装包。

| Minecraft | 平台 | Java | 状态 | Release 下载 |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.x | 17 | 当前实现；构建依赖 Forge 47.4.10 | [0.5.26-beta.1 · JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-forge-all.jar) |
| 1.20.1 | Fabric | 17 | 实验性移植；独立构建、测试与 YSM 注入检查 | [0.5.26-beta.1 · JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.20.1-fabric-all.jar) |
| 1.21.1 | Fabric | 21 | 实验性移植；独立构建、测试与 YSM 注入检查 | [0.5.26-beta.1 · JAR](https://github.com/Esnowflake/YSMRagdoll/releases/download/v0.5.26-beta.1/ysmragdoll-0.5.26-beta.1%2Bmc1.21.1-fabric-all.jar) |

## 安装与使用

1. 退出游戏，在匹配版本、加载器的实例 `mods` 中安装本模组及 YSM 2.6.5；Fabric 还需 Fabric API。
2. 配置自己的 YSM 模型资源；更新时移除旧版 YSM Ragdoll，只保留一份。
3. 在游戏按键设置中绑定本模组的设置快捷键（默认未绑定）。
4. 单人游戏死亡后会生成布娃娃；高级设置可手动创建本地测试布娃娃。
5. 开启“测试 → 牵引模式”，主手拿木棍，按住右键抓取，松开释放，滚轮调距。

多人游戏需要服务器安装本模组才能广播死亡快照。没有安装的客户端仍可加入，
但看不到布娃娃；只有客户端安装时仍可手动创建本地测试布娃娃。
物理是客户端视觉效果，不是服务器权威实体，也不跨世界保存。

## 本地构建

构建工具使用 **JDK 21**，1.20.1 编译还需 **JDK 17**。Windows 推荐 PowerShell 5.1 或 7：

```powershell
.\build.ps1 clean build
.\build.ps1 --project-dir fabric --project-prop target_mc=1.20.1 clean build
.\build.ps1 --project-dir fabric --project-prop target_mc=1.21.1 clean build
```

`build.ps1` 使用工程内 `.gradle-home/` 存储 Gradle 分发包和依赖，
`.tmp/` 存储临时文件；工程在 D 盘时，这些新增下载不进入 C 盘。
显式设置的 `GRADLE_USER_HOME` / `GRADLE_TEMP_DIR` 优先，需自行确保不指向 C 盘。
IDE 也需要将 Gradle user home 设置到工程的 `.gradle-home`；旧 C 盘缓存不会自动删除。

脚本尊重 `JAVA_HOME`。本机同时存在工程 `.jdks/` 中的 JDK 17 和系统 JDK 21、
且未指定 `JAVA_HOME` 时，会用 21 运行 Gradle、17 编译，以规避本机的 Java 17
loopback 问题。它不会自动安装 JDK。

代理环境变量优先；未设置时自动读取 Windows 系统 HTTP/HTTPS 代理。
PAC / SOCKS 需要提供 HTTP 代理端点，例如：

```powershell
$env:GRADLE_PROXY_URL = 'http://127.0.0.1:7890'
.\build.ps1 clean build
```

首次构建需准备 OpenYSM 编译依赖，下载与校验方法见
[构建指南](docs/BUILDING.md)。Linux / macOS 和 IDE 配置也见该指南。
Forge 产物在 `build/libs/`，Fabric 在 `fabric/build/<Minecraft版本>/libs/`。
不要分发普通不带依赖的 JAR。

## 开发与发布

- [更新日志](CHANGELOG.md)：未发布改动及历史版本；新 GitHub Release 说明由此生成。
- [发布流程](RELEASING.md)：标签、版本一致性检查、自动构建与校验文件。
- [团队协作](CONTRIBUTING.md)：同步作者更新、功能分支、PR 和冲突处理。
- [技术与使用手册（中文）](docs/GUIDE.zh-CN.md)：物理、坐标约定、设置、日志与性能边界。
- [历史回退记录](ROLLBACK.md)：旧开发记录，机器路径仅供历史参考。

CI 验证 PR 和主分支；标签 `vMAJOR.MINOR.PATCH`（可加 `-beta.N` / `-rc.N` 等后缀）
触发 GitHub 编译并创建草稿 Release，维护者检查后正式发布。
旧的无 `v` 标签不变。发布包使用 GitHub 构建产物，不上传开发机缓存或临时文件。

## 许可

本项目采用 [MIT](LICENSE)；提取的 OpenYSM 解析类保留其
[MIT 许可](licenses/OpenYSM-LICENSE.txt)。安装包包含 JBullet 和 vecmath 依赖。
不包含 Minecraft、完整 YSM 模组或玩家模型资源；玩家资源遵循各自作者的许可。
