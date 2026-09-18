# 构建与回退

## 当前本地修订：0.5.25 柔性牵引线

平滑圆弧修订：改用连续单段曲线，限制屏幕投影中的末端翘起与回折，延长停止后的回直过程，
颜色改为 `#33FFCC`。物理行为不变。本轮回退标签：`baseline-before-smooth-beam-arc-0.5.25`；
安装包备份：`E:\YSM Ragdoll\backups\ysmragdoll-before-smooth-beam-arc\ysmragdoll-0.5.25-all.jar`。

方向修正：曲线改为朝牵引方向弯曲，再接回实际抓取点。
修正前标签：`baseline-before-beam-direction-fix-0.5.25`；修正前安装包：
`E:\YSM Ragdoll\backups\ysmragdoll-before-beam-direction-fix\ysmragdoll-0.5.25-all.jar`。

开发分支：`fix/flexible-traction-beam`；回退标签：`baseline-before-flexible-beam-0.5.25`。
仅修改蓝线显示：玩家端向木棍附近延伸，布娃娃端呈现平滑拖尾；物理参数不变。
本轮未上传 GitHub，也未替换已发布 Release 的附件。

退出游戏后，用以下备份替换 mods 中同名安装包即可回退：

`E:\YSM Ragdoll\backups\ysmragdoll-before-flexible-beam\ysmragdoll-0.5.25-all.jar`

源码回退：保存未提交改动后，执行
`git switch -c rollback-flexible-beam baseline-before-flexible-beam-0.5.25`。
恢复本轮源码：`git switch fix/flexible-traction-beam`。
Git 历史备份：`E:\YSM Ragdoll\YSMRagdoll-before-flexible-beam.bundle`。
新版产物仍为 `build/libs/ysmragdoll-0.5.25-all.jar`。
需游戏内检查不同 FOV、左右手、快速转头与静止时的线条效果；木棍端使用视角空间偏移近似定位。

## 当前修订：0.5.25 牵引模式与惯性反馈

开发分支：`feature/traction-inertia`；版本号保持 `0.5.25`。
本轮开始前提交：`bb8da5b`；标签：`baseline-before-traction-inertia-0.5.25`。

确认工作区干净后，`git switch fix/responsive-grab-buoyancy` 可回到上一轮源码；
`git switch feature/traction-inertia` 可恢复本轮源码。有未提交改动时先保存。

退出游戏后，将 mods 中的同名 JAR 换成以下备份即可回退游戏版本：

`E:\YSM Ragdoll\backups\ysmragdoll-before-traction-inertia\ysmragdoll-0.5.25-all.jar`

Git 历史备份：`E:\YSM Ragdoll\YSMRagdoll-before-traction-inertia.bundle`。
新版仍使用 `build/libs/ysmragdoll-0.5.25-all.jar`，不要同时安装新旧包。改动尚未推送到 GitHub。

本轮加入起拉/停止/转向的有限惯性反馈，以及松开右键时的速度继承；
切换物品、打开界面和失效清理不会额外抛出。设置更名为“牵引模式”，说明主手木棍、按住右键的用法。
自动测试覆盖惯性反馈、受阻后的速度衰减、松手与取消的区别、关节连接和快速跟随；
仍需游戏内验证不同模型和不同抓取部位的摆动手感。

## 上一轮修订：0.5.25 快速抓取与液体浮力

开发分支：`fix/responsive-grab-buoyancy`；版本号保持 `0.5.25`。
本轮开始前的提交为 `75024b6`，标签为 `baseline-before-responsive-grab-buoyancy-0.5.25`。

源码回退：确认工作区干净后执行 `git switch fix/grab-joint-guard`。
恢复本轮修订：`git switch fix/responsive-grab-buoyancy`。有未提交修改时先保存。

游戏回退：退出游戏，将 mods 中的同名 JAR 换成以下备份：

`E:\YSM Ragdoll\backups\ysmragdoll-before-responsive-grab-buoyancy\ysmragdoll-0.5.25-all.jar`

Git 历史备份：`E:\YSM Ragdoll\YSMRagdoll-before-responsive-grab-buoyancy.bundle`。
新版安装包仍是 `build/libs/ysmragdoll-0.5.25-all.jar`，不能同时安装新旧包。
本轮提交只保存在本地，尚未推送到 GitHub。

新增验证覆盖每秒 8 格的连续目标跟随、下肢受阻时整具停止、移动路径碰撞补齐、
水和岩浆中的上浮/液面稳定、液体移除后下落、液体高度、缓存刷新、未加载区块与物理偏移。
自动测试使用简化刚体，仍需游戏内验证真实 YSM 模型在墙角、水池、岩浆池和浅流体中的效果。

## 上一轮修订：0.5.25 抓取关节防拉伸

开发分支：`fix/grab-joint-guard`；版本号保持 `0.5.25`。
本轮开始前的提交为 `2a3ad8d`，标签为 `baseline-before-joint-guard-0.5.25`。
`feature/gravity-gun` 保留本轮修改前的原始抓取行为。

源码回退：确认工作区干净后执行 `git switch feature/gravity-gun`。
恢复本轮修订：`git switch fix/grab-joint-guard`。有未提交修改时先保存，不要强制重置。

游戏安装包回退：退出游戏，将 mods 中本轮同名 JAR 换成以下备份，不能同时安装：

`E:\YSM Ragdoll\backups\ysmragdoll-before-joint-guard\ysmragdoll-0.5.25-all.jar`

Git 历史独立备份：`E:\YSM Ragdoll\YSMRagdoll-before-joint-guard.bundle`。
本轮构建产物仍是 `build/libs/ysmragdoll-0.5.25-all.jar`，不能只根据文件名区分新旧。
本轮修改只保存在本地，尚未推送到 GitHub。

自动测试包含连续反向拉动、障碍物场景、稳定悬停、松手下落、
关节修正不直接改写朝向，以及墙体阻止位置修正。仍需游戏内验证不同 YSM 模型、
抓手/脚/躯干、快速转头、滚轮改变距离、墙角和狭窄空间。
本阶段允许抓取点落后于准星以保持连接，不包含持握朝向控制。

## 原始版本 0.5.25：重力枪模式

原始重力枪分支：`feature/gravity-gun`。
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
