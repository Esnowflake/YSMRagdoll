# 构建与回退

## 当前版本 0.5.25：重力枪模式

当前开发分支：`feature/gravity-gun`。
重力枪改动前提交：`11041b9`；标签：`baseline-before-gravity-gun-0.5.24`。
原 `optimize/collision-cache-management` 分支仍保留在 0.5.24。
确认工作区干净后，执行 `git switch optimize/collision-cache-management` 即可回到上个版本源码。
恢复重力枪版本则执行 `git switch feature/gravity-gun`。有未提交修改时先保存，不要强制重置。

构建环境及命令见下文；当前产物为 `build/libs/ysmragdoll-0.5.25-all.jar`。
游戏内回退请退出游戏，移走新版后换用以下备份，不要同时安装两个版本：

`E:\YSM Ragdoll\backups\ysmragdoll-before-gravity-gun\ysmragdoll-0.5.24-all.jar`

历史独立备份：`E:\YSM Ragdoll\YSMRagdoll-before-gravity-gun.bundle`。这些改动尚未推送到 GitHub。

游戏内重点验证：按住/松开右键，头部与四肢抓取，墙体遮挡，滚轮上下限，切换物品及界面后释放，
目标到期或低于 Y=-64 时释放；观察实际 YSM 模型与光束的对齐。自动测试不能替代游戏内操作验证。

## 更早的 0.5.24 优化回退记录

优化分支：`optimize/collision-cache-management`。
优化前提交：`9709b64`；本地 `main` 保持在这个提交。
优化前标签：`baseline-before-cache-management-20260915`。
这些记录目前保存在本地，尚未推送到 GitHub。

## 在这台电脑构建

在 `E:\YSM Ragdoll\YSM Ragdoll` 打开 PowerShell：

```powershell
$env:JAVA_HOME = 'C:\Users\Snowflake\.cache\ysmragdoll\jdk-17.0.20.1+1'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:/Temp'
.\gradlew.bat build
```

第二行修复本机 Java 本地通信使用临时目录时出现的连接错误，仅影响当前终端启动的 Java 进程。
依赖均已缓存时可以在命令末尾加 `--offline`。
游戏安装使用 `build/libs/ysmragdoll-0.5.24-all.jar`，小体积的无 `all` 包不包含完整物理依赖。

## 游戏安装包回退

退出游戏，把游戏 mods 文件夹中的 0.5.24 移到其他文件夹备份，再换回原先使用的安装包。
不要同时安装两个版本。模组不会自动改动游戏 mods 文件夹。

另外从优化前源码重新构建了一个 0.5.23，存放在：

`E:\YSM Ragdoll\backups\ysmragdoll-before-optimization\ysmragdoll-0.5.23-all.jar`

这个备份通过构建与原有自动测试，但不等同于此前社交媒体发布包的逐字节副本，也未替代游戏实测。

## 源码回退

先用 `git status` 检查，若有尚未提交的修改，先另建分支保存或提交，不要使用强制重置。
工作区干净时可以直接切回优化前分支：

```powershell
git switch main
```

也可以从明确标签创建一条独立回退分支：

```powershell
git switch -c rollback-before-optimization baseline-before-cache-management-20260915
```

需要恢复优化版本时，切回 `optimize/collision-cache-management`。
切分支不会删除旧构建产物，构建时根据当前版本选择相应的 JAR。

## 仓库和依赖备份

- Git 历史独立备份：`E:\YSM Ragdoll\YSMRagdoll-before-cache-management.bundle`
- 编译依赖备份：`E:\YSM Ragdoll\backups\ysmragdoll-before-optimization\openysm-2.6.5-forge+mc1.20.1.jar`
- 依赖来源：本地 `OpenYSM-main/build/libs/ysm-2.6.5-forge+mc1.20.1.jar`。
- 依赖 SHA-256：`DCA3858FFEC9C5C8D542FF19D8C4228ED5B0722450DEF9E792A20AEF5982AA1A`。

如原仓库损坏，可以在另一个空目录使用 `git clone` 克隆 bundle，再把依赖备份放入新项目的
`libs/openysm-2.6.5-forge+mc1.20.1.jar`。bundle 包含优化前历史，不包含游戏配置、日志、Gradle 缓存或依赖 JAR。

## 游戏内验证

自动测试检查了缓存、寿命规则和中英文界面布局；仍需在实际 YSM 游戏环境验证：

1. 单具与多具相邻布娃娃在完整方块、半砖、楼梯上正常倒地。
2. 放置/拆除方块、开关门、活塞运动后碰撞及时更新，原有推出保护有效。
3. 管理页显示数量与逐具倒计时；应用手动清除后显示对应文字；永久模式不显示倒计时。
4. 布娃娃落到 Y=-64 以下后从列表和世界消失，手动清除模式下同样生效。
5. 窄窗口滚动正常，切换分类、应用、取消不丢失或误保存设置。
6. 切换维度或退出世界后列表清空；推动、爆炸与原有骨骼动作正常。

性能收益尚未做游戏内 FPS 对比，不据自动测试推断具体提升百分比。
