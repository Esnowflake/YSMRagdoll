# 贡献指南 / Contributing

[首页](README.md) | [文档索引](docs/README.md) | [开发与构建](docs/BUILDING.md)

欢迎提交问题报告、文档改进和代码修复。涉及新平台、大幅调整物理行为或模型适配时，请先通过
[Issue](https://github.com/Esnowflake/YSMRagdoll/issues)说明目标和兼容范围。

## 问题反馈

请提供 Minecraft、加载器、YSM 和本模组的版本，复现步骤、预期与实际表现，以及相关日志。
渲染或物理问题可附截图、视频和模型骨骼特征；提交模型文件前请确认作者允许分享。
日志位置与排查步骤见[使用指南](docs/GUIDE.zh-CN.md)。分享前请移除日志中的个人路径、服务器地址等无关信息。

## 开发流程

外部贡献者先 Fork 仓库，再从自己的副本创建分支；有仓库写权限的协作者可以直接推送功能分支。
以下命令在工作区干净且本地 `main` 跟踪本仓库时使用：

```sh
git switch main
git pull --ff-only
git switch -c docs/your-change
# 修改文件并执行相应检查
git add <changed-files>
git diff --cached
git commit -m "Describe the change"
git push -u origin docs/your-change
```

向 `Esnowflake/YSMRagdoll:main` 发起 Pull Request，说明解决的问题、行为变化和验证结果。
Fork 用户同步时从自己的 `upstream/main` 获取更新；不要强推共享分支或覆盖其他人的工作。

## 提交前检查

- 按[构建指南](docs/BUILDING.md)验证受影响的平台；共享物理或渲染改动应覆盖全部三个目标。
- 单元测试和 Mixin 注入检查不能替代游戏内验证。说明已测试的模型、单人/多人场景及尚未验证的部分。
- 文档改动检查相对链接、版本和命令；调整首页时同步更新中英文 README。
- 用户可见变化写入 [CHANGELOG.md](CHANGELOG.md) 的 `[Unreleased]`；不要自行移动已发布标签。
- 不提交运行日志、编译参数转储、个人路径、缓存、下载的依赖、玩家资源或访问凭据。

## 文档约定

README 面向玩家，介绍功能、兼容性、下载与使用。开发环境放在 `docs/BUILDING.md`，
技术契约放在 `docs/ARCHITECTURE.zh-CN.md`，发布操作放在 `RELEASING.md`，
版本历史集中维护在 changelog 和 GitHub Releases。

个人排查笔记和临时回退记录放在 Git 忽略的 `local-notes/` 中，不作为公共文档提交。
公开仓库内的文件即使以点开头或不在 README 中链接，仍然公开可见；
忽略规则也不会删除已经存在的提交历史。

## English

Bug reports should include Minecraft, loader, YSM and mod versions, reproduction steps, expected and actual
behavior, and relevant logs. Remove unrelated personal information and only share models you are allowed to distribute.

External contributors can fork; collaborators can use a feature branch in this repository. Open a pull request
against `main` describing the problem, changes and verification. Do not force-push shared branches.

Follow the [build guide](docs/BUILDING.md) and test affected platforms. Shared physics or rendering changes
should cover all three targets. Report in-game testing separately from unit and injection checks.
Keep both READMEs aligned, verify documentation links, and record user-visible changes under `[Unreleased]`.

Keep logs, local notes, dependencies, player assets and credentials out of commits. Use ignored `local-notes/`
for personal records; hidden filenames and unlinked documents in a public repository are still public.
Versioning and releases follow the [release guide](RELEASING.md).
