# WatchAI · 手表端 DeepSeek

一个给 **OPPO Watch 46mm（OW20W1，Android 8.1）** 用的极简语音 AI 助手。

按住表盘上的圆键说话 → 语音转文字 → DeepSeek 回答 → 流式显示。需要实时信息时自动联网搜索。

- **APK 253KB**，零第三方依赖（不用 AndroidX / Material / OkHttp / Gson）
- **语音识别免费**（硅基流动 XingChenASR）
- **一轮对话约 ¥0.0015～0.003**
- 适配 402×476 表盘，深色主题，大字号

---

## 一、需要准备的 API（重点看这里）

应用需要调用 3 个服务，前两个必填，第三个可选。

### 1. DeepSeek API Key —— 必填

**用途**：对话 + 联网搜索（都是同一个服务）

**获取步骤**：

1. 打开 https://platform.deepseek.com
2. 手机号注册 / 登录
3. 左侧「**API keys**」→「**创建 API key**」
4. 复制 `sk-` 开头的那串

**费用**：按 token 计费，一轮手表对话约 **¥0.0015～0.003**。
新账号可能有赠送额度，用完需充值（最低 ¥10 起）。

**用到的接口**：
- `POST https://api.deepseek.com/chat/completions`（对话，流式）
- `POST https://api.deepseek.com/v1/tools/search`（联网搜索，$0.75/千次）

> 模型名默认填 `deepseek-chat`，会自动路由到当前的基础模型。

---

### 2. 硅基流动 API Key —— 必填（语音识别）

**用途**：把录音转成文字

**获取步骤**：

1. 打开 https://cloud.siliconflow.cn
2. 手机号注册 / 登录
3. **完成实名认证**（「账户设置 → 实名认证」）
   - ⚠️ 2026-05-15 起不实名无法调用任何模型，免费的也不行
4. 左侧「**API 密钥**」→「**新建 API 密钥**」
5. 复制 `sk-` 开头的那串
6. **账户需要有余额才能调用**（哪怕调用的是免费模型）
   - 实测余额为 0 时返回 `HTTP 402 {"code":30001,"message":"Sorry, your account balance is insufficient"}`
   - 建议充 **¥1**，免费模型不会消耗这笔钱，只是作为门槛

**费用**：应用默认用 `XingChenAGI/XingChenASR-V3.2`，**免费**。

**可选的免费模型**（改 `asr_model` 配置即可）：

| 模型 | 价格 | 备注 |
|---|---|---|
| `XingChenAGI/XingChenASR-V3.2` | 免费 | **默认值**，实测 8 秒音频往返约 1.4 秒 |
| `Qwen/Qwen3-ASR-1.7B` | ¥0.00022/秒 | 约 ¥0.0011/5 秒，质量也不错 |
| `FunAudioLLM/SenseVoiceSmall` | 免费 | ⚠️ 2026-11 实测调用一律 HTTP 500，模型列表里还在但已不可用 |

---

### 3. 火山引擎豆包 —— 可选（备用通道）

**用途**：硅基流动额度耗尽时自动切换

**⚠️ 先说结论**：豆包的录音文件识别**极速版是 4.5 元/小时**，5 秒语音约 ¥0.00625，
比一节 DeepSeek 对话还贵 2～4 倍。**除非硅基流动经常不够用，否则不建议配置。**

**如果仍要配置**：

1. 打开 https://console.volcengine.com/speech/new/
   - 注意是 `/speech/new/` 新版控制台，`/speech/app` 是旧版，界面完全不同
2. 完成实名认证
3. 左侧「语音识别」→ 找到「**录音文件识别大模型 - 极速版**」→ 点「开通」
   - 名字很像的服务很多，**认准「极速版」三个字**
4. 领免费试用额度（20 小时，半年有效，需手动点「领取」）
5. 右上角「API 调用」→ 创建 API Key

**两种鉴权方式都支持**：

| 控制台版本 | 需要的值 | 填入应用的方式 |
|---|---|---|
| 新版 | `X-Api-Key`（UUID 格式） | 设置页「豆包 Key」 |
| 旧版 | `APP ID`（数字）+ `Access Token` | adb 下发 `volc_appid` / `volc_access` |

**Resource ID**：应用已写死 `volc.bigasr.auc_turbo`，不用管。

> 未开通服务时调用会返回 `45000030 requested resource not granted`。
> 火山方舟（Ark）的 Key 不能用于此服务，必须用豆包语音控制台签发的。

---

## 二、构建

不需要 Gradle，直接调 Android SDK 工具链，构建时间 < 5 秒。

### 环境要求

| 项目 | 版本 |
|---|---|
| JDK | 17 |
| Android SDK Build-Tools | 34.0.0 |
| Android Platform | android-27（API 27） |

### 国内网络提示

`dl.google.com` 在国内会被限速。可用腾讯云镜像直接下载同名文件：

```
https://mirrors.cloud.tencent.com/AndroidSDK/
```

需要下载的文件与放置位置：

| 文件 | 解压到 |
|---|---|
| `commandlinetools-mac_arm64-*.zip` | `<SDK>/cmdline-tools/latest/`（解压出的目录改名 `latest`） |
| `build-tools_r34-macosx.zip` | `<SDK>/build-tools/34.0.0/`（解压出的 `android-14` 改名） |
| `platform-27_r03.zip` | `<SDK>/platforms/android-27/`（解压出的 `android-8.1.0` 改名） |

`build-tools_r34-macosx.zip` 是 universal binary，Apple Silicon 上原生运行，不需要 Rosetta。

### 执行构建

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=$HOME/Library/Android/sdk
./build.sh
```

产物：`watchai.apk`（约 253KB）。

首次运行会自动用 `keytool` 生成 `debug.keystore`（密码 `android`）。

---

## 三、安装到手表

1. 手表上开启开发者模式：**设置 → 其他设置 → 关于手表 → 连点「版本号」7 次**
2. 打开 **USB 调试**
3. 放上磁吸充电底座，底座接电脑
4. 手表弹出「允许 USB 调试」时点允许
5. 安装：

```bash
adb install -r watchai.apk
```

> 注意：只支持 **armeabi-v7a（32 位）**，64 位包会安装失败。
> 系统版本为 Android 8.1 / API 27。

---

## 四、填入 API Key

三种方式任选，**不需要重新打包**。

### 方式 A：手表上手动填（适合日常修改）

打开应用 → 右上角 ⚙ → 分别填写

- DeepSeek API Key
- 硅基流动 Key
- 豆包 Key（可选）

→ 点「保存」

### 方式 B：adb 下发（适合首次配置，不用在表盘上敲长串）

```bash
adb shell am start -n com.yao.watchai/.MainActivity \
    -e deepseek_key sk-xxxxxxxx \
    -e asr_key sk-yyyyyyyy
```

可选参数：

| 参数 | 说明 |
|---|---|
| `-e deepseek_key ...` | DeepSeek Key |
| `-e asr_key ...` | 硅基流动 Key |
| `-e volc_key ...` | 豆包新版 API Key |
| `-e volc_appid ...` | 豆包旧版 APP ID |
| `-e volc_access ...` | 豆包旧版 Access Token |
| `-e model ...` | 指定 DeepSeek 模型名 |
| `-e reset 1` | 重置「额度用尽」断路器 |
| `-e dump 1` | 在状态行显示当前配置摘要（不显示 Key 内容） |

### 方式 C：默认值

Key 为空时应用不会崩溃，只在状态行提示「请先点右上角 ⚙ 填写 API Key」。

---

## 五、使用

| 操作 | 效果 |
|---|---|
| **按住**圆键 | 开始录音，震动一下、按钮变红、显示「松开发送」 |
| **松手** | 结束录音并发送 |
| **按住后滑出** | 取消本次录音 |
| 左上角 **☰** | 查看「上一次会话」（只读） |
| 右上角 **⚙** | 设置 |

**每次打开应用都是新会话**，历史不会带进上下文。
磁盘上最多保留 **上一次会话 + 本次会话** 两个文件，更早的自动删除。

遇到需要实时信息的问题（今天天气、最新新闻、股价等），模型会自己调用联网搜索，
搜索期间状态行显示「正在联网搜索…」。搜索结果互相矛盾时，会给出最可能的结果并标注「（推测）」。

---

## 六、成本

按每天 30 次对话（每次 5 秒语音 + 一轮回答）估算：

| 项目 | 单价 | 每天 |
|---|---|---|
| 语音识别（硅基流动 XingChenASR） | 免费 | ¥0 |
| 对话（deepseek-flash） | 谷时 $0.15/1M 输入、$0.60/1M 输出 | ≈¥0.045 |
| 联网搜索（按需触发） | $0.75 / 千次 | ≈¥0.005 |
| **合计** | | **≈¥0.05/天** |

DeepSeek 峰时段为 UTC 01:00–04:00 和 06:00–10:00（周一至周五，中国法定节假日除外），
峰时单价是谷时的 2 倍。

---

## 七、目录结构

```
app/
  AndroidManifest.xml
  res/
    values/strings.xml
    drawable-nodpi/
      ic_launcher.png        应用图标
      chatbg.png             聊天背景（402×476，预裁）
  src/com/yao/watchai/
    MainActivity.java        主界面：对话气泡 + 按住说话 + 顶部栏
    HistoryActivity.java     上一次会话（只读）
    SettingsActivity.java    填 API Key
    Config.java              配置读写 + 断路器
    SessionStore.java        会话存档（只留一轮）
    Api.java                 网络层：语音识别路由 + DeepSeek 对话 + 联网搜索
build.sh                     无 Gradle 构建脚本
run-sim.sh                   模拟器启动脚本（见下方限制）
docs/
  设计与实现.md               架构、数据流、接口契约
  语音识别方案.md             主备切换方案与成本对比
CHANGELOG.md                 版本日志与决策记录
```

---

## 八、技术要点

- **零第三方依赖**：网络用 `HttpURLConnection`，JSON 用系统 `org.json`，
  不引 AndroidX / Material / OkHttp / Gson
- **不依赖 Google 服务**：设备无 GMS，所以不能用 `SpeechRecognizer`、Firebase
- **流式输出**：解析 DeepSeek 的 SSE，逐 token 追加到同一个 TextView
  （预建气泡 + `StringBuilder` 累加，避免 1GB 内存设备上的 GC 卡顿）
- **主备语音通道**：硅基流动 → 额度耗尽 → 豆包，带断路器（6 小时自动恢复）
- **联网搜索用 tool calling**：让模型自己决定要不要搜，而不是每轮都搜

---

## 九、已知限制

| 限制 | 说明 |
|---|---|
| 无语音朗读 | 系统无 TTS 引擎，云端 TTS 会产生额外费用 |
| 无离线能力 | 1GB 内存跑不动本地 ASR/LLM |
| 每轮对话只带最近 8 条消息 | 控制 token 成本 |
| 豆包极速版较贵 | 4.5 元/小时，仅作备用 |
| 模拟器跑不起来 | API 27 的 arm64 镜像在新版 emulator 上无法启动（`/data` 初始化失败），只能真机调试 |
| 仅适配 402×476 | 布局按该分辨率手调，其他尺寸需调整 |

---

## 十、常见问题

**Q：提示「手表没有网络」**
检查手表是否连了 WiFi，或 eSIM 数据是否开通。应用会把这个错误单独识别出来，不会误判为额度问题。

**Q：提示「语音 Key 无效或未实名」**
硅基流动 Key 填错，或账号未完成实名认证。

**Q：提示「主通道额度用尽，已切备用」但备用也没配**
说明硅基流动返回了 402/429。检查账户余额（免费模型也要求余额 > 0）。

**Q：按键没反应**
首次使用需要授予麦克风权限。按住时会弹权限框，允许后重新按。

**Q：文字显示不全 / 布局错位**
应用按 `wm density 200`、`402×476` 设计。可用 `adb shell wm density` 查看当前值。

---

## 十一、免责声明

本项目为个人学习用途，与 DeepSeek、硅基流动、火山引擎无任何关联。
API 调用产生的费用由使用者自行承担。请遵守各服务商的用户协议。
