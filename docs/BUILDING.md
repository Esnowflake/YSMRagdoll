# 开发与构建 / Building

[首页](../README.md) | [文档索引](README.md) | [贡献指南](../CONTRIBUTING.md) | [发布流程](../RELEASING.md)

## 环境与目标 / Requirements

安装 Git、**JDK 21 和 JDK 17**。统一使用 JDK 21 运行 Gradle，
1.20.1 的编译工具链为 JDK 17，1.21.1 为 JDK 21。
项目自带 Gradle Wrapper 8.14.3，无需安装全局 Gradle。首次构建需要网络下载依赖。
发布脚本另需 Python 3.11+；Fabric 注入检查脚本需要 Bash（Windows 可使用 Git Bash）。

| 目标 / Target | Gradle 工程 | 编译 JDK | 输出目录 |
| --- | --- | --- | --- |
| Forge 1.20.1 | 仓库根目录 | 17 | `build/libs/` |
| Fabric 1.20.1 | `fabric/`，`target_mc=1.20.1` | 17 | `fabric/build/1.20.1/libs/` |
| Fabric 1.21.1 | `fabric/`，`target_mc=1.21.1` | 21 | `fabric/build/1.21.1/libs/` |

以下命令均从仓库根目录执行。请确认 `JAVA_HOME` 指向自己的 JDK 21 安装目录；
JDK 17 应能被 Gradle toolchain 发现，必要时用
`--project-prop org.gradle.java.installations.paths=<JDK17目录>` 指定位置。

## 准备 OpenYSM 编译依赖 / Parser dependency

三个目标共享以下固定依赖。构建只提取所需解析类并保留许可，不将完整 OpenYSM 模组打入发行包。
下载地址和 SHA-256 与 CI 固定值一致，依赖保存在 Git 忽略的 `libs/` 中。

PowerShell：

```powershell
New-Item -ItemType Directory -Force libs | Out-Null
$url = 'https://github.com/OpenYSM/OpenYSM/releases/download/ysm-2.6.5-forge%2Bmc1.20.1/ysm-2.6.5-forge%2Bmc1.20.1-all.jar'
$file = 'libs/openysm-2.6.5-forge+mc1.20.1.jar'
Invoke-WebRequest -Uri $url -OutFile $file
$expected = '46eac038c314e7df1af80deea3c9e507f9f1e7ddf66b04eec455560dbfd51909'
if ((Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'OpenYSM checksum mismatch'
}
```

Bash（Linux / macOS / Git Bash）：

```sh
mkdir -p libs
curl --fail --location --retry 3 \
  'https://github.com/OpenYSM/OpenYSM/releases/download/ysm-2.6.5-forge%2Bmc1.20.1/ysm-2.6.5-forge%2Bmc1.20.1-all.jar' \
  -o libs/openysm-2.6.5-forge+mc1.20.1.jar
echo '46eac038c314e7df1af80deea3c9e507f9f1e7ddf66b04eec455560dbfd51909  libs/openysm-2.6.5-forge+mc1.20.1.jar' | sha256sum --check
```

macOS 可将 `sha256sum --check` 替换为 `shasum -a 256 -c`。
校验不通过时停止构建并重新检查下载来源。

## 构建与单元测试 / Build and test

Windows PowerShell 5.1 / 7：

```powershell
.\build.ps1 clean build
.\build.ps1 --project-dir fabric --project-prop target_mc=1.20.1 clean build
.\build.ps1 --project-dir fabric --project-prop target_mc=1.21.1 clean build
```

Bash：

```sh
bash ./gradlew --no-daemon clean build
bash ./gradlew --no-daemon --project-dir fabric -Ptarget_mc=1.20.1 clean build
bash ./gradlew --no-daemon --project-dir fabric -Ptarget_mc=1.21.1 clean build
```

`build` 包含单元测试。测试报告位于各目标输出目录上一级的 `reports/tests/test/index.html`。
安装测试使用 `*-all.jar`；普通 JAR 不包含完整运行依赖。
正式发行附件由发布工作流添加 Minecraft 和加载器标识，见[发布流程](../RELEASING.md)。

## Fabric Mixin 注入检查 / Injection checks

CI 额外使用对应平台的官方 YSM 2.6.5 二进制验证 Mixin 注入。先在 Bash 中执行：

```sh
bash scripts/download-ysm.sh fabric-1.20.1
bash scripts/download-ysm.sh fabric-1.21.1
bash ./gradlew --no-daemon --project-dir fabric -Ptarget_mc=1.20.1 -Pysm_smoke=true clean build
bash ./gradlew --no-daemon --project-dir fabric -Ptarget_mc=1.21.1 -Pysm_smoke=true clean build
```

下载文件仅用于兼容性测试，保存在忽略的 `libs/compatibility/`，不随本模组分发。
注入检查不启动游戏，不能证明模型选择、世界渲染、死亡流程和多人游戏均正常。

## 发布脚本检查 / Release tooling

```sh
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/release.py validate v0.5.26-beta.1
```

示例标签对应当前 `gradle.properties` 的 `mod_version`；修改版本后同步替换标签，
并在 changelog 中添加对应章节。完整打包与发布步骤见[发布流程](../RELEASING.md)。

## 网络、缓存与 IDE / Network, caches and IDE

Windows 的 `build.ps1` 默认使用项目内 `.gradle-home/` 和 `.tmp/`，并保留调用前的环境。
代理环境变量优先于系统代理；使用 PAC 时需明确设置 `GRADLE_PROXY_URL` 为实际 HTTP 代理地址。
下载命令不会自动继承构建脚本的代理逻辑：按需为 `Invoke-WebRequest` 添加 `-Proxy`，
或为 curl 配置 `HTTPS_PROXY`。不要将代理凭据提交到仓库。

Linux / macOS 如需将缓存放在项目内，可在构建前设置 `GRADLE_USER_HOME="$PWD/.gradle-home"`。
Java 的依赖下载需通过 `GRADLE_OPTS` 或本地 Gradle 配置设置 HTTP/HTTPS 代理；
仅设置 `HTTPS_PROXY` 不保证 Java 使用该代理。

IDE 分别导入根目录 Forge 工程和 `fabric/` 工程，选择 Wrapper 与 JDK 21 作为 Gradle JVM。
IDE 不一定调用 `build.ps1`，请单独配置缓存、代理和 JDK 17 toolchain。
不要提交 IDE 个人状态或本机绝对路径。

## 游戏内验证 / In-game checks

按改动范围检查模型加载、死亡生成、复活后的快照独立性、地形碰撞、牵引、爆炸、液体与世界切换。
网络改动还需验证多人游戏及未安装模组的客户端加入。
记录所用模型和平台；未执行的检查应明确标注，不能用编译成功代替游戏验收。

## English quick start

Install JDK 21 and 17, run Gradle on JDK 21, and make JDK 17 discoverable as a toolchain.
Run all commands from the repository root. Download and verify the pinned OpenYSM dependency above,
then use the PowerShell or Bash build commands for the three targets.

Use `*-all.jar` from each target's output directory. Fabric injection checks additionally require the
official test-only YSM binaries downloaded by `scripts/download-ysm.sh`. Python 3.11+ is required for
release tooling. Build and injection checks do not replace in-game testing.

For proxy configuration, `build.ps1` reads environment settings before the Windows system proxy.
Dependency download commands and IDE builds may need separate configuration.
See [Releasing](../RELEASING.md) for tags, package verification and release drafts.
