# 发布流程 / Releasing

[贡献指南](../CONTRIBUTING.md) | [构建文档](BUILDING.md) | [更新日志](../CHANGELOG.md)

本文面向负责发布的维护者；安装与下载请见 [首页](../README.md)。

## 支持范围

发布矩阵固定为 **Forge 1.20.1、Fabric 1.20.1、Fabric 1.21.1**。
不构建 Forge 1.21.1 或 NeoForge。Gradle 8.14.3 使用 JDK 21 运行，
1.20.1 产物用 JDK 17 编译，1.21.1 用 JDK 21 编译。
Fabric 是实验性移植；通过编译、单元测试和真实 YSM 类的 Mixin 注入检查，
仍需在正式发布前完成游戏内验收。

## 日常维护

- 功能分支通过 PR 合入 `main`，CI 执行 Java 测试和完整构建。
- 用户可见变更写到 `CHANGELOG.md` 的 `## [Unreleased]` 下。
- 使用 `### Added / 新增`、`### Changed / 变更`、`### Fixed / 修复`、
  `### Removed / 移除`、`### Security / 安全` 分类；无内容的分类不必保留。
- 不在 README 重复维护历史日志；新版本统一使用 `CHANGELOG.md`。

## 正式发布

1. 发布 PR 更新 `gradle.properties` 的 `mod_version`。
2. 将本次已完成改动从 `[Unreleased]` 移到 `## [X.Y.Z] - YYYY-MM-DD`；
   使用实际发布日期。保留新的 `[Unreleased]`，不为旧版本编造日期。
3. 本地完整构建，运行发布脚本测试，并完成游戏内检查。
4. PR 审查通过、CI 成功后合入 `main`。
5. 从已合并的干净主分支创建并推送附注标签，例如：

   ```sh
   git switch main
   git pull --ff-only origin main
   git tag -a v0.5.27 -m "YSM Ragdoll 0.5.27"
   git push origin v0.5.27
   ```

6. GitHub Release 工作流检查标签/版本/changelog，下载并校验依赖，执行
   `clean build`（含单元测试与重混淆），检查 JAR 元数据、内嵌依赖、许可证
   及必需类，生成校验文件和发行说明。
7. 三个 build job 全部成功后，独立 publish job 校验完整三包及哈希，
   创建 **Draft Release（草稿）**，任一目标失败都不会发布残缺矩阵。
   维护者检查测试报告、附件和更新日志后，在 GitHub 点击 Publish release。

模组文件分别命名为 `ysmragdoll-0.5.27+mc1.20.1-forge-all.jar`、
`ysmragdoll-0.5.27+mc1.20.1-fabric-all.jar`、
`ysmragdoll-0.5.27+mc1.21.1-fabric-all.jar`。
构建目录中的普通 `-all.jar` 不改名，发布准备阶段添加平台标识。
构建不在开发机执行；草稿附件由 GitHub 从对应标签源码编译。

## 预发布与安全重跑

支持 `vX.Y.Z-alpha.N`、`vX.Y.Z-beta.N`、`vX.Y.Z-rc.N`（N 从 1 开始）。
项目版本和 changelog 章节必须包含同一后缀；Release 自动标记为 prerelease，
仍先创建草稿。无 `v` 的历史标签保持原样。

构建失败时重跑失败任务；publish 失败时优先重跑失败 job，复用原构建产物。
已有草稿可补齐缺失附件；已存在附件必须逐字节相同，否则拒绝覆盖。
已公开 Release 一律拒绝修改。完全重跑 build 可能改变 JAR 时间戳，
不会因此覆盖旧草稿附件，需要人工判断并处理草稿。
代码有错时发布新 patch，不能移动旧标签或静默替换公开附件。

## 下载校验

把三个 JAR 和 SHA256SUMS 附件下载到同一目录进行完整校验：

```sh
sha256sum --check ysmragdoll-0.5.27-SHA256SUMS.txt
```

PowerShell 用 `Get-FileHash -Algorithm SHA256 <jar>` 与文件中哈希比较。
校验文件无开发机路径，生成说明不复用历史 JAR 的哈希。

## GitHub 权限

CI 和 release 的 build job 只有只读权限；独立 publish job 才有
`contents: write`，使用内置 `GITHUB_TOKEN`，不保存个人 token。
依赖和 Wrapper 分发包均固定版本及哈希；相同标签发布串行执行。
管理员应保护主分支和 `v*` 标签，限制发布者并要求审查。
脚本校验不能替代 GitHub 权限和分支规则。

## 多版本接入标准

- 分离平台入口、事件、配置、网络与 YSM 模型捕获适配。
- 适配对应 Minecraft 映射、渲染 API 和 Java 版本。
- 提供正确的 `mods.toml` 或 `fabric.mod.json`，不能改名 Forge JAR 冒充 Fabric。
- 固定对应 YSM/OpenYSM 依赖、哈希和兼容范围。
- 各变体单独执行测试、完整构建和游戏启动验证。

完成后才向构建矩阵添加变体；publish job 等待所有变体成功后汇总发布。
目前没有启用未实现的矩阵项。

## English Summary

The supported release matrix is Forge 1.20.1, Fabric 1.20.1 and Fabric 1.21.1.
Maintain `[Unreleased]`, create a version section,
bump `mod_version`, and merge a reviewed PR after tests and in-game checks.
Tag the merged commit as `vMAJOR.MINOR.PATCH`; alpha/beta/rc numbered suffixes
are also supported. GitHub builds the source, verifies the JAR, generates notes
from the changelog, and creates a **draft** with platform-specific assets.
Review the draft before publishing. Prereleases are marked automatically.

Build jobs are read-only; only the separate publishing job has write access.
Retries can resume drafts but cannot overwrite different assets or modify
published releases. Never rewrite public tags. New loaders and game versions
require real ports and runtime validation before joining a build matrix.
