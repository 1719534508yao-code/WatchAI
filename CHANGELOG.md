# 更新日志

本文件记录每一个版本的功能变更、修复与决策。新版本写在上方。

版本号规则：`v0.x` 开发迭代；`v1.x` 稳定可用；`v2.x` 架构性变更。

---

## v0.8 — 2026-11-11

视觉主题化 + 字号层级 + 矛盾搜索规则。

### 新增
- **应用图标**：改为立绘头像（`res/drawable-nodpi/ic_launcher.png`，192×192，中心裁方）
- **聊天背景**：立绘铺满 + 黑色蒙层 `#B8000000`（72%），保证文字对比度
  - 图片预裁成 402×476 精确匹配屏幕比例，无黑边
  - `applyBackground()` 用 `LayerDrawable` 叠 `ColorDrawable`，资源缺失时自动退回纯色
  - 主界面和历史页共用同一背景
- **矛盾搜索结果规则**：写进 system prompt
  > 搜索结果互相矛盾时，不要回答"无法确定"：选出最可能的一个（来源更权威、多方一致的优先）直接给出，末尾加「（推测）」两个字。

### 变更
| 元素 | 原 | 现 |
|---|---|---|
| 用户气泡文字 | 16sp | **14sp** |
| AI 回答文字 | 19sp | **22sp** |
| AI 行距 | 4dp | 5dp |
| 顶栏上边距 | 14dp | **20dp** |
| 历史按钮 ☰ | 15sp | **18sp** |
| 齿轮 ⚙ | 16sp | **19sp** |
| 标题 AI | 13sp | 14sp |
| 强调色 | `#1E88E5` 浅蓝 | **`#4E6AB1` 立绘深靛蓝** |
| AI 气泡 | 不透明 `#1C1C22` | 半透明 `#E01C1C24`（背景透出） |
| 用户气泡 | 不透明浅蓝 | `#F24E6AB1` 半透明深靛蓝 |
| 状态行 | 无投影 | 加 `setShadowLayer`，压在背景图上仍可读 |

### 移除
- 首屏的「你好，按住下面的圆键说话。」引导语

### 决策记录
- **强调色取自立绘头发**：用 ffmpeg 采样三个区域得 `#4E6AB1` / `#4E6AAD` / `#637BB1`，
  取中间值。比原来的 `#1E88E5` 更沉，在白字和深色背景之间对比度更稳。
- **蒙层用 72% 而不是更淡**：更淡的话文字对比度不足；更浓则立绘失去辨识度。
- **气泡改半透明**：完全不透明会把背景图完全挡住，半透明既透出图案又不影响读字。

### 已知问题
- APK 体积从 32KB 涨到 **304KB**（背景图 178KB + 图标 51KB）。
  对手表来说仍可忽略，但不再是「200KB 以内」。

---

## v0.7 — 2026-11-11

历史查看 + 联网搜索。

### 新增
- **左上角历史按钮**（`☰`）→ `HistoryActivity`
  - 只读展示「上一次会话」，更早的已在启动时覆盖删除
  - 空态显示「没有上一次会话」
- **联网搜索**：接入 DeepSeek 官方 Search API
  - 端点 `POST https://api.deepseek.com/v1/tools/search`
  - 用 **tool calling** 让模型自己决定要不要搜，而不是每轮都搜
  - `Api.round()` 流式累积 `tool_calls` 分片 → 执行搜索 → 把结果作为 `tool` 消息回灌 → 第二轮不带工具，强制出最终答案
  - 搜索只取摘要（`need_content=false`），结果裁到 5 条，控制 token
  - 搜索期间状态行显示「正在联网搜索…」
  - 系统提示词追加一句：需要实时信息时先调 `web_search`

### 决策记录
- **用 tool calling 而不是「每轮都搜」**：Search API 是 $0.75/千次 ≈ ¥0.0054/次，
  比一轮对话（¥0.0015～0.003）还贵。无条件搜索会让成本翻倍，且给简单问题引入噪声。
- **第二轮关掉工具**：防止模型反复调工具、陷入搜索循环。
- **只取摘要不取全文**：`need_content=false`，避免把整页 HTML 变 Markdown 塞进上下文。

### 验证
- 真机 `-e debug_text "今天北京天气怎么样"` → 正确触发搜索 → 回答「北京今天晴，最高约15℃，
  昼夜温差大、早晚冷，夜间起有轻雾」，是实时数据，非模型记忆

---

## v0.6 — 2026-11-11

主备切换落地 + 三个真机 bug 修复。

### 新增
- `Api.transcribe()` 改为路由器：硅基流动 →（额度用尽/失败）→ 豆包极速版
- `QuotaException` + 断路器：`Config.primaryExhausted()` / `markPrimaryExhausted()` / `clearExhausted()`，
  6 小时自动恢复
- 豆包「录音文件识别·极速版」实现：`POST /api/v3/auc/bigmodel/recognize/flash`，
  **base64 直传音频，不需要公网 URL**，响应看 `X-Api-Status-Code` 头
- 设置页增加豆包 Key 输入框；保存时顺带重置断路器
- adb 调试指令统一由 `handleExtras()` 处理，`onCreate` / `onNewIntent` 都会走：
  `-e reset 1`、`-e dump 1`、`-e debug_audio /path`、`-e volc_key ...`

### 修复
1. **403 被误判为「额度用尽」**：key 填错、未实名也返回 403，原逻辑无脑当成额度问题，
   导致断路器被误触发，之后所有请求都跳过主通道、且提示「未配置语音识别 Key」——完全误导。
   现在 403 必须响应体里出现额度类字样才算；401 / `token is invalid` 单独归为鉴权错误。
2. **adb 参数在应用已运行时被忽略**：`reset` / `dump` / `debug_audio` 只写在 `onCreate`，
   走 `onNewIntent` 时全部失效。统一收口到 `handleExtras()`。
3. **`effAsrKey()` 回退到 DeepSeek key**：没配语音 Key 时会拿 DeepSeek 的 key 去请求硅基流动，
   拿到 403 进而触发上面第 1 条。改为只认真实的语音 Key，没配就跳过主通道。

### 实测发现
- **SenseVoiceSmall 已不可用**：模型列表里还在，但调用一律 HTTP 500。
  改用同样免费的 `XingChenAGI/XingChenASR-V3.2`，8 秒中文音频往返约 1.4 秒，标点完整。
- **硅基流动免费模型也要求账户有余额**：实测返回
  `{"code":30001,"message":"Sorry, your account balance is insufficient"}` HTTP 402，
  连 `Qwen2.5-7B` 这类免费对话模型也一样。**账户余额为 0 时，免费模型也调不了。**

### 验证
- **对话链路完全跑通**（真机）：`-e debug_text "用一个字回答：天空是什么颜色"` →
  用户气泡 + AI 气泡「蓝」，流式输出正常，会话存档正常
- 语音链路：调试音频 → 主通道请求 → 402 → 断路器标记 → 提示切换备用（备用未配 Key）
- `-e reset 1` / `-e dump 1` 均生效，`dump` 输出 `ds=35 asr=51 volc=0 exhausted=false`
- 新增调试指令 `-e debug_text "..."`：跳过语音，直接验证对话与渲染

---

## v0.5 — 2026-11-11

新增语音识别主备切换方案文档（尚未实现代码）。

### 新增
- `docs/语音识别方案.md`：
  - 三级降级链：硅基流动（免费）→ 豆包录音文件识别极速版 → 豆包一句话识别
  - 切换判定规则（HTTP 402/403/429、响应体关键词、连续失败 3 次）
  - 断路器设计（`EXHAUSTED` 状态 + 冷启动/6 小时自动恢复）
  - 两种豆包接口的差异对比与选型理由
  - 实现改动清单与待验证项

### 调研结论
- **录音文件识别（0.8 元/小时）比一句话识别（3.5 元/千次）便宜 3.2 倍**，
  但标准版和大模型版都要求提供**公网可下载的音频 URL**，手表无法直接使用
- 找到折中端点 `POST /api/v3/auc/bigmodel/recognize/flash`，单次同步返回、无需 URL，
  实测端点在线（错误 key 返回 `Invalid X-Api-Key`），可用作备用首选
- 请求体格式（base64 / raw）待拿到真 key 后验证

### 决策记录
- **网络不可达不触发切换**：换供应商同样连不上，只会让用户多等一轮超时
- **断路器而非每次重试**：避免额度耗尽后每次对话都先白白等一次主供应商超时

---

## v0.4 — 2026-11-11

交互反馈与排版微调。

### 新增
- **按下震动反馈**：`ACTION_DOWN` 时立即震动 30ms，不等录音器初始化完成
  - API 26+ 走 `VibrationEffect.createOneShot`，以下走 `vibrate(long)`
  - 录音启动失败时震动 150ms 作为区别提示
  - 新增 `VIBRATE` 权限
- **按下形变反馈**：按钮缩放到 0.90（70ms），松手回弹到 1.0（90ms），
  配合原有的变红 + 文案改「松开发送」

### 变更
- 顶栏下移：根布局顶部内边距 4dp → 14dp（实测边界 `[7,5]` → `[7,17]`）
- **AI 回答字号 16sp → 19sp**，行距 2dp → 4dp；用户自己的气泡保持 16sp
- 文档新增 4.2.1 节「语音识别方案对比」，含四家官方价格的实测数据与换供应商的代码成本说明
- 错误信息中文化 + 截断到 42 字：
  - `Unable to resolve host` / `Network is unreachable` → 「手表没有网络」
  - `timeout` → 「网络超时」

### 验证
- 模拟长按 3 秒：按钮变红、缩放到 0.90、状态行「正在听…」均正确
- 松手后走完整链路：录音 → 上传 → 错误回显，进程未崩溃

### 发现的问题（待解决）
- **手表当前无网络**：`Active default network: none`，
  `mDataConnectionState=0`，LTE 信号 -120dBm（极弱），WiFi 关闭
- 这是设备侧问题，不是应用问题。应用把 `Unable to resolve host` 正确翻译成了「手表没有网络」

---

## v0.3 — 2026-11-11

上真机、放大字号、打通 Key 下发。

### 新增
- `MainActivity.applyIntentConfig()`：支持通过 adb 直接下发 Key
  ```bash
  adb shell am start -n com.yao.watchai/.MainActivity \
      -e deepseek_key sk-xxx -e asr_key sk-yyy [-e model deepseek-chat]
  ```
  - 走 Intent extra 而不是 `run-as` 写 SharedPreferences，**正式版也能用**
  - `onNewIntent` 里同样处理，应用已在前台时也能更新
- 手表已安装并配置 DeepSeek Key（`sk-a884…`），实测 `/chat/completions` 返回正常，
  `deepseek-chat` 实际路由到 `deepseek-flash`

### 变更（字号普遍上调 1～3sp）
| 元素 | 原 | 现 |
|---|---|---|
| 对话气泡 | 14sp | 16sp |
| 说话按钮 | 17sp | 20sp |
| 状态行 | 11sp | 12sp |
| 顶栏标题 | 12sp | 13sp |
| 设置页输入框 | 13sp | 15sp |
| 设置页按钮 | 14sp | 15sp |
| 设置页说明 | 10sp | 11sp |

### 决策记录
- **Key 下发走 Intent 而非 SharedPreferences 文件**：`run-as` 要求 APK 是 debuggable，
  而 debug 包在生产设备上会带来额外风险；Intent extra 在 release 包上同样有效，
  且不影响用户之后在设置页手动修改。

---

## v0.2 — 2026-11-11

会话生命周期与提示词策略调整。

### 新增
- `SessionStore`：会话存档
  - 每次启动调用 `beginNewSession()`，把 `session_current.json` 顶替为 `session_last.json`
  - 更早的存档被覆盖删除，磁盘上永远最多只有「上一次会话 + 本次会话」两个文件
  - 存档**只做记录，永远不会被读回来当上下文**

### 变更
- **每次打开应用 = 新会话**：`history` 为内存态，启动即为空
- **恢复 system prompt**，但压缩到约 40 字：
  > 你在手表上回答。极简：能一句话说清就不说第二句，准确优先于客套。不用 Markdown、不用列表、不解释过程、不复述问题。中文。
- 用户消息与 AI 回复在产生时同步写入 `SessionStore`

### 决策记录
- **提示词为什么又加回来了**：第一次实现时按"零注入"做，但手表 402×476 的屏幕撑不住长回答，
  模型默认会解释过程、复述问题、列举要点——这些在表盘上只会把人挤到要滑动。
  加回一段极短的系统提示，换来输出长度直降，整体 token 反而更省。
- **提示词是静态字符串常量**：DeepSeek 命中 prompt cache 时输入按 $0.003/M 计费（对比 cache miss $0.15/M），
  这 40 个字几乎不产生成本，甚至可以忽略。
- **上一轮会话不参与上下文**：用户要求"最干净的上下文"。存档保留只是为了将来能做历史浏览，
  不会塞回请求里。

### 真机验证
- 安装到 OPPO Watch 46mm 成功（`adb install -r` → Success）
- 主界面渲染正常，`uiautomator` 实测按钮边界 `[126,319][276,469]`，未超出 402×476
- 设置页在 320dpi 下文字清晰可读

### 未完成
- **模拟器验证放弃**：API 27 的 arm64 镜像在新版 emulator（37.3.9）上起不来——
  surfaceflinger 正常，但客户机时钟停在开机 2 分钟后不再推进，`/data/dalvik-cache` 建不出来，
  `system_server` 始终没起来。试过 `-wipe-data`、`-partition-size 2048`、`-gpu swiftshader_indirect` 均无效。
  改为真机直接验证。

### 已知问题
- 存档目前没有浏览入口，`SessionStore.previousSession()` 暂无调用方
- 模拟器不可用（见上），后续调 UI 只能靠真机截图

---

## v0.1.1 — 2026-11-11

构建环境与本地模拟器。应用代码不变。

### 新增
- `run-sim.sh`：一键创建 402×476 / 密度 200 / API 27 的 AVD 并安装启动
- 文档补充「国内网络下的工具链获取」

### 环境变动
- 安装 OpenJDK 17（`brew install openjdk@17`）
- `dl.google.com` 在国内被限速（148MB 的 commandlinetools 卡在 73MB 不动），
  改用**腾讯云镜像** `mirrors.cloud.tencent.com/AndroidSDK/` 直取：
  - `commandlinetools-mac_arm64-15859902_latest.zip`（148MB / 22 秒）
  - `build-tools_r34-macosx.zip` → `sdk/build-tools/34.0.0`
  - `platform-27_r03.zip` → `sdk/platforms/android-27`
  - `emulator-darwin_aarch64-16863232.zip` → `sdk/emulator`
  - `sys-img/android/arm64-v8a-27_r02.zip` → API 27 系统镜像
- 跳过 `brew install --cask android-commandlinetools`（卡在 Google 下载）

### 验证
- `build.sh` 编译通过，产物 **24KB**
- `apksigner verify`：v1 / v2 / v3 签名均通过
- `classes.dex` 含 `Api` / `Config` / `MainActivity` / `SettingsActivity` 全部类

### 决策记录
- **选 API 27 的 arm64 系统镜像做模拟器**：与手表系统版本完全一致，
  避免"在 API 34 上能跑、装到 API 27 手表上崩溃"这类问题。
- **build-tools 用 34.0.0 而不是更老的版本**：它是 universal binary，含 arm64 原生，
  Apple Silicon 上不需要 Rosetta；编译目标仍是 `minSdk 21 / targetSdk 27`，不影响产物。

---

## v0.1 — 2026-11-11

首个可运行版本。目标是把「按住说话 → AI 回复」这条链路完整跑通。

### 新增
- `MainActivity`：单页对话界面
  - 顶部「AI」标题 + 齿轮设置入口
  - 中部对话气泡区（`ScrollView`，用户靠右、AI 靠左）
  - 底部 120dp 圆形「按住说话」按钮
  - 状态行显示：按住说话 / 正在听… / 识别中… / 思考中… / 错误信息
- `Api.transcribe()`：录音文件 multipart 上传至硅基流动 SenseVoiceSmall，返回文本
- `Api.chat()`：DeepSeek `/chat/completions` 流式对话，逐 token 回调
- `Config`：SharedPreferences 读写，语音 Key 留空时自动复用 DeepSeek Key
- `SettingsActivity`：填 DeepSeek Key / 语音 Key / 模型
- `build.sh`：无 Gradle 手动构建（aapt2 → javac → d8 → zipalign → apksigner）
- `docs/设计与实现.md`：完整设计文档

### 决策记录
- **不用 Gradle**：项目零依赖，Gradle + AGP 要下载数百 MB，收益为零。直接调 SDK 工具链。
- **不引 AndroidX / Material / OkHttp / Gson**：为了 APK 体积和 API 27 兼容性。网络用 `HttpURLConnection`，JSON 用系统 `org.json`。
- **targetSdk 固定 27**：与设备系统版本一致，避免 API 28+ 的明文流量限制和后台限制带来的行为差异。
- **预建空气泡 + StringBuilder**：流式输出时不反复创建/读取 View，避免 1GB 内存设备上的 GC 卡顿。
- **只保留最近 8 条消息**：控制 token 成本。
- **System prompt 禁用 Markdown**：手表上无法渲染，星号会原样显示。
- **只做文字、不做朗读**：设备无系统 TTS 引擎，云端 TTS 会产生额外费用，用户明确不需要。

### 设备适配
- 按 OPPO Watch 46mm（`OW20W1`）实测参数设计：Android 8.1 / API 27 / armeabi-v7a / 402×476 / 422MB 可用内存
- 布局按 `wm density 200`（322dp 宽）设计

### 已知问题
- 历史记录不持久化，退出应用即清空
- 无 TTS 朗读
- 免费 ASR 有 RPM 限流
- 手表上手工输入长 Key 体验差，需借助 adb 写入

---

## 待办

- [ ] 模拟器验证通过
- [ ] 真机安装验证
- [ ] TTS 朗读（可选）
- [ ] 历史持久化
- [ ] ASR 供应商切换验证（火山引擎）
