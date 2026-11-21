# 奔奔王国自动化闭环（MuMu 本地实测）

## 目前已实现的自动化环节

- GitHub Actions 每次源代码变更：执行离线 JUnit 回归测试，失败则不发布 APK
- 构建：集成 Kingshot MIT 图片模板、中文 OCR，生成 APK 构建产物
- 可选 MuMu 实机 E2E：自动从构建产物安装 APK，触发七项独立测试，收集匿名化任务分类
- 测试中游戏截图、账号、聊天内容：**仅保存在 Windows 本地，不自动上传 GitHub 或其他云端**
- 每项状态为 ACTION/SKIP/FAIL/BLOCKED；ACTION 只表示执行了动作，不等于游戏结果已完成

## 一次性接入测试电脑（需要用户本人完成）

1. 在专用 Windows 测试环境开启 MuMu、ADB、720×1280。测试游戏账号预先登录。最好使用隔离的 Windows 用户和测试账号。
2. 打开仓库 **Settings → Actions → Runners → New self-hosted runner**，按 GitHub 指示安装 Windows Runner，给 Runner 加自定义标签 `benben-mumu`。不要使用权限过大的 Windows 管理员日常账号。
3. 安装 Android platform-tools 并使 `adb` 出现在 Runner 的 PATH 中，或设环境变量 `BENBEN_ADB_PATH` 为 adb.exe 路径。如同时连接多个模拟器，设 `BENBEN_ADB_SERIAL`。
4. 若国内游戏包名不是 `com.got.china`，将实际包名写入 `BENBEN_GAME_PACKAGE`。首次需要在测试 MuMu 为助手授予「显示在其他应用上层」。Root 环境保持与已测试配置一致。
5. 在仓库 **Settings → Secrets and variables → Actions → Variables** 创建变量：
   - `BENBEN_MUMU_E2E=enabled`：允许执行 MuMu 本地实测
   - 可选 `BENBEN_MUMU_ON_PUSH=true`：每次 GitHub 构建通过后自动运行测试，默认关闭
6. 第一次建议先在 Actions 手动触发 `BenBen standalone APK` 的 `workflow_dispatch` 验证连接，之后再按需开启每次提交自动运行。

## 安全限制

- 仓库是公开仓库。自托管 Runner 在测试机上执行仓库脚本，有代码供应链风险；请使用**专用、隔离的测试机/虚拟机和账号**，不要放个人文件、业务密码、云服务密钥。
- 测试任务可能操作游戏，部分游戏可能禁止脚本；自行评估账号风险。
- Runner 必须保持在线、MuMu 必须可连接、游戏需保持已登录，否则无法执行真实游戏回归。
- 本方案不会让 ChatGPT 在聊天结束后继续自动写代码；自动 CI/E2E 能持续运行测试并保留结果，新的代码修复仍需要打开工作会话或额外接入受授权的代码代理。
- 仍然需要真实游戏结果判定（例如行军队列变化、训练计时出现），**不能仅凭点击成功就声称七项功能通过**。

## 代码入口

- CI：`.github/workflows/benben-apk.yml`
- Windows E2E：`.github/scripts/benben-mumu-e2e.ps1`
- 仅 Debug APK 的启动 Intent：`com.xnjtg.benben.RUN_LOCAL_E2E`
- 本地回归：`benben-android/app/src/test/java/com/xnjtg/benben/RewardExitPlannerTest.java`
