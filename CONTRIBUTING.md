# 团队协作 / Contributing

[简体中文首页](README.md) | [English home](README.en.md)

## Collaborator 与 Fork 的区别

你是原仓库的 Collaborator，有权限向允许写入的分支推送。
作者和你使用同一个远端仓库，不需要额外创建 Fork 来“同步原作者”。
`origin/main` 是你上次获取到的远端主分支记录，`main` 是本地主分支。
作者推送不会自动改动你的本地文件；`git fetch` 只更新远端记录。
自动化构建使用独立的 GitHub Actions 工作流；协作分支仍以本仓库为准。

## 推荐日常流程

先提交当前改动到自己的功能分支，或在切换前明确保存它们。
以下命令假设工作区干净；不要强制覆盖未提交改动。

```sh
git switch main
git fetch origin
git pull --ff-only origin main
git switch -c codex/my-change
# edit and test
git add <changed-files>
git diff --cached
git commit -m "Describe the change"
git push -u origin codex/my-change
# open a pull request targeting Esnowflake/YSMRagdoll:main
```

`--ff-only` 只允许本地主分支直接向前移动；双方各自有提交时会停止，
不会悄悄制造合并。尽量不要直接在本地 `main` 开发。

## 作者在你开发时更新了主分支

在自己的功能分支、工作区干净时执行：

```sh
git fetch origin
git merge origin/main
# resolve conflicts and test
git push
```

Merge 保留双方提交历史，适合已经推送、可能被别人使用的分支。
Rebase 可整理仅自己使用的未共享提交，但会重写提交 ID；不要擅自 rebase
共享主分支，也不要用 `git push --force` 覆盖作者工作。

## 冲突不等于丢失代码

不同文件通常可自动合并；同一处被双方修改、删除与修改相遇时可能冲突。
即使没有文本冲突，也可能有逻辑冲突，因此必须重新测试。

1. `git status` 查看冲突文件。
2. 编辑 `<<<<<<<`、`=======`、`>>>>>>>` 区域，保留正确的组合逻辑。
3. `git add <resolved-files>`，运行测试，再 `git commit` 完成合并。
4. 要取消本次合并，可执行 `git merge --abort`；不要硬重置丢弃改动。

## 能自动同步吗

- 自动 fetch：可以让 IDE 定期检查作者的新提交，不修改你的工作文件。
- 自动 pull/merge：不建议对正在开发的工作区开启，可能冲突或破坏当前状态。
- PR 自动合并：管理员允许并配置必要检查、审查规则后，可在通过后自动合并；
  不等于自动替你解决冲突。
- 定时覆盖分支：不建议，会掩盖冲突和覆盖开发意图。

建议管理员保护 `main`：要求 PR、至少一人审查、CI 成功，禁止强推和删除。
写权限不等于管理员权限，Collaborator 未必能修改这些规则。

## 更新日志与版本

日常用户可见改动写入 `CHANGELOG.md` 的 `[Unreleased]`。
发布时归入新版本章节并更新 `gradle.properties`；不要改写已发布标签。
历史 `releases/` 文档保留，新发布以 changelog 为唯一日志输入。
详见 [发布流程](RELEASING.md)。

## English Summary

Collaborators work against the same upstream; a fork is optional.
Fetch updates remote-tracking references without touching your files.
On clean local `main`, run `git pull --ff-only origin main`, then create a feature
branch and open a PR. To bring upstream changes into a shared feature branch,
run `git fetch origin` and `git merge origin/main`, resolve conflicts, test and
push. Do not force-push shared history.

Automatic fetch is appropriate for checking updates. Unattended pull/merge does
not replace conflict resolution or testing. Administrators can require CI and
review and may enable PR auto-merge. Keep changes in `[Unreleased]` until release.
