# 奔奔独立助手 V2

这是独立研发的 AutoX.js 任务执行引擎。当前版本提供文本识别、冷却控制、失败保护和本地日志，联盟互助/每日奖励为待实测原型。

功能清单来自第三方 APK 的 UI 配置解析，但未恢复收费脚本的执行逻辑。**本项目尚未实现全部收费功能，也尚未形成经过测试的独立 APK。**

使用流程：将 `assistant.js` 导入 AutoX.js。默认 `dryRun=true`，首次运行会在 /sdcard/BenbenIndependent/config.json 写入配置，测试后才能改为 false。创建 /sdcard/BenbenIndependent/STOP 文件可停止任务。