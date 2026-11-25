# 知微应用限制 / Zhiwei App Limiter

**没给自启动权限的应用，一离开前台就被强行停止。**

**Apps without auto-start permission are force-stopped the moment they leave the foreground.**

---

## 这是什么 / What is this

一个用 Shizuku 提权实现的 Android 后台限制工具。不靠无障碍、不靠轮询动画帧，只做一件事：把不该在后台待着的应用干掉。

A Shizuku-powered background restriction tool for Android. No accessibility service, no frame polling. It does one thing: kills apps that have no business running in the background.

---

## 工作原理 / How it works

```
Shizuku（ADB shell 权限，uid 2000）
        ↓
每 800ms 读一次当前前台应用（dumpsys activity）
        ↓
受管应用离开前台 → am force-stop 立即执行
        ↓
同时用 appops / deviceidle 从系统层堵住自启动
```

- **不需要 root**，只需要装好 [Shizuku](https://github.com/RikkaApps/Shizuku) 并授权
- **不用无障碍服务**，因为无障碍常驻的开销比一次轻量 dumpsys 大得多——本应用的目的恰恰是给系统减负
- **配置随时生效**，改规则不用重启服务

---

## 功能 / Features

| 功能 | 说明 |
|---|---|
| 离开前台即强停 | 受管应用一旦不在前台，立刻 `am force-stop` |
| 自启动开关 | 逐个应用控制是否允许自启动 |
| 后台驻留开关 | 允许某些应用留在后台（比如音乐、下载） |
| 系统层限制 | `appops RUN_ANY_IN_BACKGROUND=ignore` + 移出省电白名单 |
| 开机自启 | 重启后自动恢复守护 |
| 事件日志 | 记录每次强停，可回溯 |

---

## 使用 / Usage

1. 安装 [Shizuku](https://github.com/RikkaApps/Shizuku)，用无线调试或 root 方式启动它
2. 安装本应用，打开后点「授权」给 Shizuku 权限
3. 在「全部应用」里挑要限制的应用，加进「已限制」
4. 打开「守护开关」
5. 建议把本应用加进电池优化白名单（首次启动会提示），否则守护可能被系统掐掉

---

## 权限说明 / Permissions

| 权限 | 用途 |
|---|---|
| `QUERY_ALL_PACKAGES` | 读取已安装应用列表 |
| `FOREGROUND_SERVICE` | 守护服务常驻 |
| `POST_NOTIFICATIONS` | 显示守护状态通知 |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复守护 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 避免被系统掐掉 |

**本应用不联网，不上传任何数据。**

**No network access. No data collection.**

---

## 构建 / Build

正式包由 GitHub Actions 自动构建，本机构建仅用于调试。

Release builds are produced by GitHub Actions. Local builds are for debugging only.

```bash
./gradlew assembleDebug
```

推送 `v*` 标签会自动触发构建并发布 Release。

Pushing a `v*` tag triggers the build and publishes a Release.

---

## 环境 / Environment

- Kotlin + Jetpack Compose (Material 3)
- minSdk 24 / targetSdk 35
- AGP 9.0.0 / Gradle 9.1.0 / JDK 17
- 依赖 Shizuku API 13.1.5

---

## 官方渠道 / Official

- 联系邮箱 / Email：ailixiya@qycxkjit.wecom.work
- 资源分享群 / QQ Group：**1137595303**
- GitHub：https://github.com/mingxiozsg-star

---

## 许可 / License

本项目仅供学习与个人使用。使用本应用造成的任何后果由使用者自行承担。

For learning and personal use only. Use at your own risk.
