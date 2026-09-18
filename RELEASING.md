# 发布流程 / Releasing

[首页](README.md) | [文档索引](docs/README.md) | [开发与构建](docs/BUILDING.md) | [更新日志](CHANGELOG.md)

本文面向维护者。玩家安装包见 [GitHub Releases](https://github.com/Esnowflake/YSMRagdoll/releases)。

## 发布矩阵

| 目标 | 运行 Java | 发行文件名 |
| --- | --- | --- |
| Forge 1.20.1 | 17 | `ysmragdoll-<version>+mc1.20.1-forge-all.jar` |
| Fabric 1.20.1 | 17 | `ysmragdoll-<version>+mc1.20.1-fabric-all.jar` |
| Fabric 1.21.1 | 21 | `ysmragdoll-<version>+mc1.21.1-fabric-all.jar` |

`<version>` 为项目版本，不含标签前缀 `v`。三个目标均使用 JDK 21 运行 Gradle，
按目标使用 JDK 17 / 21 编译。当前没有 Forge 1.21.1 或 NeoForge 构建。

Fabric 仍为实验性移植；编译、单元测试及真实 YSM 类的 Mixin 注入检查通过，
不等于游戏内验收完成。正式发布前应完成对应平台的游戏验证，并如实记录已知限制。

## 1. 准备版本

1. 在发布分支更新根目录 `gradle.properties` 的 `mod_version`；Fabric 共用此版本。
2. 将 `CHANGELOG.md` 的 `[Unreleased]` 内容归入 `## [版本号] - YYYY-MM-DD`，
   保留空的 `[Unreleased]` 供后续开发使用。每个发布版本至少有一条变更。
3. 按[构建指南](docs/BUILDING.md)执行三平台构建、发布脚本测试及相关游戏内检查。
4. 提交 Pull Request，列出验证结果和未完成项；CI 通过并审查后合入 `main`。

版本支持 `X.Y.Z`、`X.Y.Z-alpha.N`、`X.Y.Z-beta.N` 和 `X.Y.Z-rc.N`（N 从 1 开始）。
项目版本、changelog 章节与标签去掉 `v` 后必须完全一致。

## 2. 校验并推送标签

以下为 **PowerShell** 示例，从干净且最新的主分支执行。它从版本文件读取实际版本，
避免示例版本与代码不一致：

```powershell
git switch main
git pull --ff-only origin main
# 确认 git status --short 无输出，且当前提交就是待发布提交
git status --short
$version = (Get-Content gradle.properties | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
$tag = "v$version"
python -m unittest discover -s scripts -p 'test_*.py'
if ($LASTEXITCODE -ne 0) { throw 'Release tooling tests failed' }
python scripts/release.py validate $tag
if ($LASTEXITCODE -ne 0) { throw 'Release metadata validation failed' }
git tag -a $tag -m "YSM Ragdoll $version"
if ($LASTEXITCODE -ne 0) { throw 'Could not create release tag' }
git push origin $tag
```

仅在完成版本准备后执行。若版本已有标签，创建会失败；不要删除或移动公开标签。
无 `v` 的历史标签保持原样，新工作流监听 `v*` 标签。

## 3. 检查自动构建与草稿

[Release 工作流](.github/workflows/release.yml)从标签源码执行：

1. 校验版本与 changelog，下载并检查固定依赖的 SHA-256。
2. 为三个目标运行 `clean build`，Fabric 额外执行 YSM 注入检查。
3. `scripts/release.py prepare <tag> --target <target>` 校验每个目标的 JAR 元数据、
   内嵌依赖、许可证和必需类，添加发行文件名。
4. 三个目标全部成功后，`assemble <tag>` 汇总完整矩阵并校验哈希。
5. 独立 publish job 创建 **Draft Release（发行草稿）**，上传三个安装包和
   `ysmragdoll-<version>-SHA256SUMS.txt`。预发布后缀会自动设置 prerelease。

本地构建用于预先验证；公开发行候选附件由 GitHub Actions 从标签源码重新构建。
只读 build job 不具备发布权限，只有 publish job 使用 `contents: write` 和内置 `GITHUB_TOKEN`。

在 GitHub 的 Actions 和 Releases 页面核对提交、测试报告、三份附件、校验文件及发行说明，
确认游戏验证结论与版本状态一致后再点击 **Publish release**。
更新中英文 README 的下载表与版本状态时，以已经公开且实际存在的附件为准。

## 下载校验

完整校验需要将三个 JAR 和校验文件放在同一目录：

```sh
sha256sum --check ysmragdoll-<version>-SHA256SUMS.txt
```

将 `<version>` 替换为实际版本；macOS 使用 `shasum -a 256 -c`。
只下载单个 JAR 时，可用 PowerShell 计算哈希并与校验文件中同名条目逐字比对：

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath '.\下载的安装包-all.jar'
```

## 失败重跑与回退

构建失败时修复原因并重跑相应任务；publish 失败时优先重跑 publish job，复用已验证产物。
已有草稿可以补齐缺失附件；同名附件必须逐字节相同，否则脚本拒绝覆盖。
已公开 Release 不允许由发布脚本修改。完整重建可能改变 JAR 时间戳，需要核对草稿差异。

代码问题使用新版本修复，不移动旧标签、不静默替换公开安装包。
玩家回退时从 [Releases](https://github.com/Esnowflake/YSMRagdoll/releases) 下载匹配环境的旧包，
退出游戏，移除新包后再放入旧包。

## 文档与产物维护

版本日志集中在 `CHANGELOG.md`，发行说明由对应章节生成。
`releases/` 与 `release/` 保留早期历史说明和产物，不作为当前下载或构建输入。
新的安装包通过 GitHub Releases 分发，不提交到源码目录。

新增平台前必须完成实际移植、依赖固定、独立构建测试和游戏内验证，再扩展矩阵。
不要通过重命名安装包宣称支持另一个加载器。

## English

The release matrix contains Forge 1.20.1, Fabric 1.20.1 and Fabric 1.21.1. Run Gradle on JDK 21;
compile 1.20.1 with JDK 17 and 1.21.1 with JDK 21. Fabric remains experimental until in-game acceptance
testing is completed.

Bump `mod_version`, move completed changes from `[Unreleased]` into a dated version section,
run the builds and release-tool tests, and record in-game verification. Merge the reviewed release PR,
validate metadata, then create and push an annotated `v<version>` tag from clean, current `main`.
Numbered alpha, beta and rc suffixes are supported.

The release workflow builds all three packages from the tag and verifies metadata, dependencies, licenses
and checksums before creating a **draft**. Only the publish job has write permission.
Review all assets, reports and notes before publishing. Update both README download tables only after
the public assets exist.

Retries can resume matching drafts but cannot overwrite different assets or modify published releases.
Never move public tags or silently replace packages; publish a new version for fixes.
Historical `release/` and `releases/` files are not current build inputs.
