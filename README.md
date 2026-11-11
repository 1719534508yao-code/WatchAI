# ⌚ WatchAI

> 给你的安卓手表装一个能说话的 DeepSeek。

![platform](https://img.shields.io/badge/Android-8.1%20%2F%20API%2027-3DDC84?logo=android&logoColor=white)
![abi](https://img.shields.io/badge/ABI-armeabi--v7a-orange)
![size](https://img.shields.io/badge/APK-253KB-brightgreen)
![deps](https://img.shields.io/badge/dependencies-0-blue)

**按住表冠上的圆键说话 → 松开 → AI 回答。** 就这么简单。

为 [OPPO Watch 46mm（OW20W1）](https://www.oppo.com/cn/) 打造，402×476 方屏、Android 8.1、无 Google 服务。
零第三方依赖，不用 AndroidX / Material / OkHttp / Gson，APK 只有 253KB。

---

## 📖 目录

- [它长什么样](#-它长什么样)
- [能做什么](#-能做什么)
- [5 分钟上手](#-5-分钟上手)
- [操作教程](#-操作教程)
- [API Key 怎么申请](#-api-key-怎么申请)
- [花了多少钱](#-花了多少钱)
- [常见问题](#-常见问题)
- [开发者指南](#-开发者指南)
- [已知限制](#-已知限制)

---

## 📱 它长什么样

```
┌────────────────────────┐
│ ☰   AI              ⚙  │   ☰ 历史   ⚙ 设置
│                        │
│ ┌────────────────────┐ │
│ │ 明天北京早晨几度   │ │   ← 你说的话（小字，靠右）
│ └────────────────────┘ │
│ ┌────────────────────┐ │
│ │ 明天北京早晨约 1℃，│ │   ← AI 回答（大字，靠左）
│ │ 晴，偏北风 1~2 级。│ │
│ └────────────────────┘ │
│                        │
│        按住说话         │
│       ╭────────╮       │
│       │  按住  │       │   按住录音，松开发送
│       │  说话  │       │
│       ╰────────╯       │
└────────────────────────┘
```

深色主题，背景是角色立绘，气泡半透明让图透出来，文字始终清晰。

---

## ✨ 能做什么

| 能力 | 说明 |
|---|---|
| 🎤 **按住说话** | 按住圆键录音，松手自动识别并发送。按下有震动和变色反馈 |
| 💬 **流式回答** | 回答逐字显示，不用干等 |
| 🌐 **自动联网搜索** | 问「今天天气」「最新新闻」时，模型自己决定去搜 |
| 🤔 **搜索结果矛盾时** | 不糊弄你，直接给最可能的结果并标注「（推测）」 |
| 🗂 **每次打开新会话** | 上下文干干净净，不会越聊越贵 |
| 🕘 **查看上一次会话** | 左上角 `☰`，只保留上一轮，更早的自动删除 |
| 💰 **极低成本** | 语音识别免费，一轮对话约 ¥0.0015～0.003 |
| 📦 **极小体积** | 253KB，装上几乎没有存在感 |

---

## 🚀 5 分钟上手

### 第 1 步：准备两个 API Key（约 3 分钟）

| 服务 | 用途 | 费用 |
|---|---|---|
| [DeepSeek](https://platform.deepseek.com) | 对话 + 联网搜索 | 约 ¥0.0015/轮 |
| [硅基流动](https://cloud.siliconflow.cn) | 语音识别 | **免费** |

详细申请步骤见 [API Key 怎么申请](#-api-key-怎么申请)。

拿到两个 `sk-` 开头的字符串后继续。

### 第 2 步：拿到 APK

**方式 A**：从 [Releases](../../releases) 下载现成的 `watchai.apk`

**方式 B**：自己编译（约 5 秒）

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=$HOME/Library/Android/sdk
./build.sh
```

> 需要 JDK 17 + Android Build-Tools 34 + Platform android-27。
> 国内下载 SDK 被限速的话见 [开发者指南](#国内网络下的-sdk-获取)。

### 第 3 步：装到手表

**① 打开开发者模式**

手表 → `设置` → `其他设置` → `关于手表` → 连点 `版本号` **7 次**
→ 返回 `设置`，此时会多出 `开发者选项`

**② 打开 USB 调试**

`设置` → `开发者选项` → 打开 `USB 调试`

**③ 连接电脑**

把手表放上**磁吸充电底座**，底座用数据线接电脑。
手表屏幕会弹出「允许 USB 调试吗？」，点**允许**。

**④ 安装**

```bash
adb devices          # 确认能看到设备
adb install -r watchai.apk
```

看到 `Success` 就成了。桌面上会出现一个新的应用图标。

### 第 4 步：填入 Key（不需要重新打包）

**最快的方式** —— 用命令直接写进去，省得在表盘上敲长串：

```bash
adb shell am start -n com.yao.watchai/.MainActivity \
    -e deepseek_key sk-你的DeepSeek密钥 \
    -e asr_key sk-你的硅基流动密钥
```

**或者在手表上手动填**：
打开应用 → 右上角 `⚙` → 依次填入 → 点「保存」

填完回到主界面，状态行显示「按住说话」就代表配置成功了。

### 第 5 步：开始用

按住下面的蓝色圆键，说话，松手。

---

## 📘 操作教程

### 主界面

| 位置 | 元素 | 作用 |
|---|---|---|
| 左上角 | `☰` | 查看上一次会话 |
| 中间 | `AI` | 标题 |
| 右上角 | `⚙` | 设置（填 Key） |
| 中部 | 对话气泡 | 蓝色靠右是你说的，深色靠左是 AI 回答 |
| 下部 | 状态行 | 显示「按住说话 / 正在听 / 识别中 / 思考中 / 正在联网搜索」 |
| 底部 | 蓝色圆键 | 按住说话 |

### 说话

**按住**圆键 →
会在同一瞬间发生三件事：**震动一下**、按钮**变小并变红**、文字变成「松开发送」，
状态行显示「正在听…」。

**松手** → 自动识别并发送。

**按住后手指滑出按钮** → 取消本次录音。

> 第一次使用时手机会弹出麦克风权限请求，点允许即可。

### 看懂状态行

| 显示 | 含义 |
|---|---|
| `按住说话` | 就绪，可以按了 |
| `正在听…` | 正在录音，松手即发送 |
| `识别中…` | 音频正在转文字 |
| `思考中…` | AI 正在生成回答 |
| `正在联网搜索…` | 模型判断需要实时信息，正在搜索 |
| `太短了，再按一次` | 录音不足 1 秒，被忽略 |
| `没听清，再说一次` | 识别结果为空 |
| `手表没有网络` | 设备没联网，不是应用的问题 |

### 查看历史

点左上角 `☰` → 显示**上一次**会话的完整对话（只读）。
点左上角 `‹ 返回` 回到主界面。

> 只保留**最近一次**会话。每次打开应用，上一次的会被顶替，更早的直接删除。

### 修改 Key

点 `⚙` → 改完点「保存」。
保存时会自动重置「额度用尽」的断路器。

### 换模型

设置页第三栏是模型名，默认 `deepseek-chat`。
填别的 DeepSeek 模型名即可切换。

---

## 🔑 API Key 怎么申请

### DeepSeek（必填）

1. 打开 https://platform.deepseek.com
2. 手机号注册 / 登录
3. 左侧菜单 `API keys` → `创建 API key`
4. 复制 `sk-` 开头的那串

**费用**：按 token 计费。新账号可能有赠送额度，用完需充值（最低 ¥10）。

应用会调用两个接口：
- `POST /chat/completions` —— 对话
- `POST /v1/tools/search` —— 联网搜索（$0.75 / 千次）

### 硅基流动（必填，用于语音识别）

1. 打开 https://cloud.siliconflow.cn
2. 手机号注册 / 登录
3. **完成实名认证** → `账户设置` → `实名认证`
   > ⚠️ 2026-05-15 起，未实名无法调用任何模型，**免费的也不行**
4. 左侧菜单 `API 密钥` → `新建 API 密钥`
5. 复制 `sk-` 开头的那串

> ⚠️ **账户需要有余额，哪怕调用免费模型**
> 余额为 0 时会返回 `HTTP 402 Sorry, your account balance is insufficient`。
> 充 **¥1** 即可，免费模型不会消耗这笔钱，只是门槛。

**默认用的模型**：`XingChenAGI/XingChenASR-V3.2` —— **免费**，实测 8 秒中文音频往返约 1.4 秒。

想换的话在设置页改模型名：

| 模型 | 价格 | 备注 |
|---|---|---|
| `XingChenAGI/XingChenASR-V3.2` | 免费 | 默认值，推荐 |
| `Qwen/Qwen3-ASR-1.7B` | ¥0.00022/秒 | 约 ¥0.0011/5 秒 |
| `FunAudioLLM/SenseVoiceSmall` | 免费 | ⚠️ 实测 HTTP 500，已不可用 |

### 火山引擎豆包（可选，备用通道）

> 💡 **先说结论：不建议配。**
> 豆包的「录音文件识别极速版」是 **4.5 元/小时**，5 秒语音约 ¥0.00625，
> 比一节 DeepSeek 对话还贵 2～4 倍。只有硅基流动经常不够用时才值得折腾。

<details>
<summary>点开看申请步骤</summary>

1. 打开 https://console.volcengine.com/speech/new/
   > 必须是 `/speech/new/` 新版控制台。`/speech/app` 是旧版，界面完全不同
2. 完成实名认证
3. 左侧 `语音识别` → 找「**录音文件识别大模型 - 极速版**」→ 点「开通」
   > 名字很像的服务很多，认准「**极速版**」三个字
4. 领免费试用额度（20 小时 / 半年，需**手动点「领取」**）
5. 右上角 `API 调用` → 创建 API Key

**两种鉴权方式都支持**：

| 控制台 | 需要什么 | 怎么填 |
|---|---|---|
| 新版 | `X-Api-Key`（UUID 格式） | 设置页「豆包 Key」 |
| 旧版 | `APP ID` + `Access Token` | adb: `-e volc_appid ... -e volc_access ...` |

Resource ID 已写死 `volc.bigasr.auc_turbo`，不用管。

**常见报错**：

- `45000030 requested resource not granted` → 服务没开通
- `Invalid X-Api-Key` → 用了火山方舟（Ark）的 Key。**方舟和豆包语音是两套东西，Key 不通用**

</details>

---

## 💰 花了多少钱

按每天 30 次对话（每次 5 秒语音 + 一轮回答）估算：

| 项目 | 单价 | 每天 |
|---|---|---|
| 语音识别 | 免费 | ¥0 |
| 对话 | 谷时 $0.15/1M 输入 · $0.60/1M 输出 | ≈¥0.045 |
| 联网搜索 | $0.75 / 千次 | ≈¥0.005 |
| **合计** | | **≈¥0.05 / 天** |

**一个月不到 2 块钱。**

> DeepSeek 峰时段为 UTC 01:00–04:00 和 06:00–10:00（周一至周五，中国法定节假日除外），
> 峰时单价是谷时的 2 倍。北京时间减去 8 小时看。

---

## ❓ 常见问题

<details>
<summary><b>提示「手表没有网络」</b></summary>

手表没联网。检查 WiFi 是否打开，或 eSIM 数据是否已开通。
应用能区分「网络问题」和「额度问题」，不会误报。
</details>

<details>
<summary><b>提示「语音 Key 无效或未实名」</b></summary>

硅基流动的 Key 填错了，或者账号还没完成实名认证。
</details>

<details>
<summary><b>提示「主通道额度用尽，已切备用」但备用也没配</b></summary>

硅基流动返回了 402（余额不足）或 429（限流）。
去硅基流动控制台看看余额——**免费模型也要求余额 > 0**。
</details>

<details>
<summary><b>按住没反应</b></summary>

1. 第一次用要授权麦克风，按住时会弹权限框
2. 如果之前拒绝过：设置 → 应用 → WatchAI → 权限 → 打开麦克风
</details>

<details>
<summary><b>安装失败 INSTALL_FAILED_NO_MATCHING_ABIS</b></summary>

这台手表只支持 **32 位（armeabi-v7a）**。确认你装的是本项目的 APK，不是别的 64 位包。
</details>

<details>
<summary><b>文字太大/太小、布局错位</b></summary>

应用按 `402×476` 屏幕、`wm density 200` 设计。查看当前设置：

```bash
adb shell wm size
adb shell wm density
```

恢复出厂密度用 `adb shell wm density reset`，改成别的值用 `adb shell wm density 200`。
</details>

<details>
<summary><b>想清空所有配置重来</b></summary>

```bash
adb shell pm clear com.yao.watchai
```
</details>

---

## 🛠 开发者指南

### 环境要求

| 项目 | 版本 |
|---|---|
| JDK | 17 |
| Android Build-Tools | 34.0.0 |
| Android Platform | android-27 |

### 构建

不使用 Gradle，直接调 SDK 工具链，构建时间 < 5 秒：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=$HOME/Library/Android/sdk
./build.sh
```

流程：`aapt2 compile` → `aapt2 link` → `javac` → `d8` → `zipalign` → `apksigner`

首次运行会自动生成 `debug.keystore`（密码 `android`，**不要提交到仓库**）。

### 国内网络下的 SDK 获取

`dl.google.com` 在国内会被限速（实测 148MB 的文件卡在 73MB 不动）。
用腾讯云镜像下载同名文件：`https://mirrors.cloud.tencent.com/AndroidSDK/`

| 文件 | 解压到 |
|---|---|
| `commandlinetools-mac_arm64-*.zip` | `<SDK>/cmdline-tools/latest/`（目录改名 `latest`） |
| `build-tools_r34-macosx.zip` | `<SDK>/build-tools/34.0.0/`（`android-14` 改名） |
| `platform-27_r03.zip` | `<SDK>/platforms/android-27/`（`android-8.1.0` 改名） |

`build-tools_r34-macosx.zip` 是 universal binary，Apple Silicon 原生运行，不需要 Rosetta。

### 调试指令

应用内置了几个 adb 调试入口，不用重新打包：

```bash
# 查看当前配置（不显示 Key 内容，只显示长度）
adb shell am start -n com.yao.watchai/.MainActivity -e dump 1

# 重置「额度用尽」断路器
adb shell am start -n com.yao.watchai/.MainActivity -e reset 1

# 用指定音频文件跑一遍完整链路（识别 → 对话）
adb push test.wav /sdcard/Android/data/com.yao.watchai/files/test.wav
adb shell am start -n com.yao.watchai/.MainActivity \
    -e debug_audio /sdcard/Android/data/com.yao.watchai/files/test.wav

# 跳过语音，直接测对话
adb shell am start -n com.yao.watchai/.MainActivity -e debug_text "你好"
```

### 目录结构

```
app/
  AndroidManifest.xml
  res/
    values/strings.xml
    drawable-nodpi/
      ic_launcher.png           应用图标
      chatbg.png                聊天背景（402×476 预裁）
  src/com/yao/watchai/
    MainActivity.java           主界面：气泡区 + 按住说话 + 顶部栏
    HistoryActivity.java        上一次会话（只读）
    SettingsActivity.java       填 Key
    Config.java                 配置读写 + 断路器
    SessionStore.java           会话存档（只保留一轮）
    Api.java                    网络层：语音路由 + DeepSeek 对话 + 联网搜索
build.sh                        无 Gradle 构建脚本
docs/
  设计与实现.md                 架构、数据流、API 契约
  语音识别方案.md               主备切换方案与成本对比
CHANGELOG.md                    版本日志与决策记录
```

### 技术要点

- **零第三方依赖**：`HttpURLConnection` + 系统 `org.json`
- **不依赖 Google 服务**：设备无 GMS，所以不能用 `SpeechRecognizer`、Firebase
- **流式输出**：解析 DeepSeek SSE，逐 token 追加到同一个 TextView
  （预建气泡 + `StringBuilder` 累加，避免 1GB 内存设备的 GC 卡顿）
- **主备语音通道**：硅基流动 → 额度耗尽 → 豆包，带断路器（6 小时自动恢复）
- **联网搜索用 tool calling**：让模型自己判断要不要搜。Search API 是 $0.75/千次，
  比一轮对话还贵，无条件搜索会让成本翻倍

更详细的设计说明见 [docs/设计与实现.md](docs/设计与实现.md)。

---

## ⚠️ 已知限制

| 限制 | 说明 |
|---|---|
| 无语音朗读 | 系统无 TTS 引擎，云端 TTS 会产生额外费用 |
| 无离线能力 | 1GB 内存跑不动本地 ASR / LLM |
| 只带最近 8 条消息 | 控制 token 成本 |
| 每次打开即新会话 | 这是设计，不是 bug |
| 只适配 402×476 | 其他分辨率需要手调布局 |
| 模拟器跑不起来 | API 27 的 arm64 镜像在新版 emulator 上无法启动，只能真机调试 |
| 未适配圆表盘 | 当前布局按方形屏设计 |

---

## 📄 免责声明

本项目为个人学习用途，与 DeepSeek、硅基流动、火山引擎**无任何关联**。
API 调用产生的费用由使用者自行承担。请遵守各服务商的用户协议。

角色立绘版权归原作者所有，仅作个人使用，如需商用请自行替换。
