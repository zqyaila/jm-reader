# JM Reader

一个原生 **Kotlin + Jetpack Compose** 安卓漫画阅读器。

> [!IMPORTANT]
> **免责声明** — 这是**非官方的第三方客户端**，仅供个人使用与技术学习。
> 本应用与 18comic / JMComic / 禁漫天堂 **无任何关联**，未获其认可或授权。
> 应用展示的漫画内容版权归原作者所有，由第三方源提供；**本应用自身不托管、不存储任何内容**。
> 请尊重版权，并遵守你所使用的内容来源的服务条款。

[English Readme →](README_EN.md)

## 功能

- 📖 **漫画浏览** — 首页（推荐 / 最新）、分类、每周必看、日/周/月榜、热门标签、随机推荐
- 🆔 **作品 ID 处处可见** — 每张封面右上角都标注 `JM` 号，详情页可一键复制，
  方便核对、引用或直接拿去搜索
- 💬 **评论区** — 作品详情页内嵌该作的评论（`GET /forum?mode=manhua&aid=`）：
  头像 / 等级 / 时间 / 点赞，楼中楼回复自动按 `parent_CID` 归位并可折叠；
  登录后可直接发评论或回复（`POST /comment`），未登录时给出明确的登录入口
- 🔎 **自适应搜索** — 只有一个输入框，**不用再选「作品 / 作者 / ID」**：
  输入关键词走站内搜索（同时命中标题、作者与标签），输入纯数字 / `JM123456` / 作品链接则
  直接跳转作品；命中作者名时结果自动按作者分组，支持翻页加载更多。
  搜索框空白时显示**搜索记录**（本机保存，重复搜索只置顶不重复，可一键清空）
- 📚 **原生阅读器** — 竖向 / 横向翻页、章节切换、进度条、自动**去打乱还原图片**
  （部分漫画页面以「水平条带倒序」方式分发，本应用会自动还原）。
  阅读工具：点左侧 1/3 后退、右侧 1/3 前进、点中间收起控制栏；音量键 / 方向键翻页；
  双指缩放、双击复位；长按把当前页存进相册（`Pictures/JMReader`）；按页书签，可从书签列表
  直接跳回；竖向模式支持自动滚动；标题可点回作品详情
- 🌐 **三语界面** — 简体中文 / 繁體中文 / English，随时切换
- ⬇️ **一键下载** — 整本下载（全部章节、全部页面），去打乱后存到
  `Download/JMReader/<专辑ID>/`，并支持内置**离线阅读**（无需网络）。
  **多线程加速**：页面按 3 路并发抓取（章节的读取参数也并发获取），单页失败自动重试一次；
  文件名带 `.jm` 后缀并以通用 MIME 登记，**不会出现在系统相册里**（相册按扩展名归类，
  末位不是图片扩展名就不会被当成图片），文件仍是普通 JPEG，用文件管理器可见、可经 USB 拷贝。
  旧版本下载的 `.jpg` 会在启动时自动改名归档，无需重新下载
- 🕘 **浏览历史（本机 + 云端）** — 历史页分「本机记录」与「云端记录」两栏：
  本机记录无需登录、离线可用，记住上次读到的章节与页码，可单条删除或一键清空；
  云端记录读的是账号在服务端的观看记录（`GET /watch_list`，需登录），翻页加载。
  书库默认打开历史页，不会因为未登录而整页被挡
- 🛡️ **崩溃日志** — 应用崩溃后，下次启动自动弹窗显示报错信息，可一键「复制日志」
- 👤 **会员功能** — 登录 / 注册、收藏、观看历史、每日签到、个人资料；
  失败时显示服务端原文（例如「无效的用户名和/或密码！」），不再只抛一个 `401`；
  **启动时自动签到**（已登录且今天未签到时静默完成，签到与否由服务端 `signedToday` 判定，
  因此重复启动不会重复签，也不需要本地记「上次签到日期」）
- 🗂️ **界面（阅读档案，默认）** — 参照 [Wenku8_Flutter](https://github.com/mikufoxxx/Wenku8_Flutter)
  的「档案」语言重做：暖纸底 `#F4F3EF` + 纯墨色 `#181818`，**没有强调色**，封面是页面上
  唯一的彩色；3dp 封面 / 4dp 面板的近直角、1px 发丝分隔、9sp 等宽「JM-123456」档案号、
  报头左侧的实心墨块与 4dp 小方块，构成印刷目录的感觉。详情页点开即滑入**固定版式**
  （封面图版、登记数字、章节行都是骨架），数据到达后就地填充，不再出现转圈加载。
  应用图标换成黑白墨色版本，
  自适应前景 + 圆形图标同源。毛玻璃只保留在悬浮底栏，
  背景是纸面 + 48dp 极淡网格。深色主题为中性石墨，不带暖色偏移。首页、书库与**详情页**
  （作品档案：封面图版 + 登记簿数字 + 编号章节行）用同一套语言，从书架点进作品不再换风格
- 🧭 **浮动底栏（照参考复刻）** — 260dp 玻璃胶囊、四个 62dp 舱位，**图标和文字常驻**
  （不再只给选中项显示文字）；选中是一个连续位置：文字加粗、颜色随手指靠近渐变、
  下方 12×1.5dp 短横线按距离淡入。可以按住横向拖过整个胶囊，松手吸附到最近一栏；
  按下时整条胶囊有 120ms 的轻微下沉与外扩。底部展示位与滚动留白随之重新对齐
- 🎨 **墨与火（可选）** — 上一版界面完整保留：深墨底 + 单一琥珀强调色、12/16/24dp 圆角、
  暖色光晕背景。在「我的 → 外观」里切换，默认是「档案」，选择会记住
- 📚 **堆叠书架** — 收藏、历史、下载与排行页顶部都是一条书脊排成的书架：书一本本立在架上，只有
  中间那本被抽出，露出封面和书名；左右滑动逐本切换、惯性滚动、整本吸附，停稳时一次
  触觉反馈。书架不是贴图：每本书按平行投影（`depthX 0.66` / `depthY −0.30`）画出
  封面面、书脊面和顶面，权重 `w = cos²(|d| / 1.48 · π/2)` 同时决定抽出量
  `0.84·cover·w·(1.24 − 0.24w)` 与抬升高度，相邻书脊再按 `railY(d) = 2.5d + 0.45d²`
  起伏。**书架的循环不是「滑到头再补」**：内部按参考项目的做法挂一个固定书池
  （`count + 2×edgeSlots` 本，每格永远显示同一本书），滚动只改一个会取模的浮点坐标
  `logicalFocus`，所以越过第一本时画面逐像素等价、没有任何重排或重挂载，滑多久都不会漂或闪。
  书架是页面里的第一项，往下翻时会一起滑走，不是钉在上面的横幅；
  不在正中的书先滑到中间。居中后点封面或书脊：被点的封面**脱离书架回正**（取的是它未斜切的
  布局矩形），同时从书池里消失（参考的 Hero 会把源孩子交给 overlay，这里同样不留副本），
  与页面交换同一个 420ms 时钟飞向左上角的封面图版；落点处页面自己那张图**与之共用同一 URL 和
  同一解码宽度**，所以交接没有重新解码、也不会闪一下骨架灰块。封面按实际显示尺寸解码，滑动时
  不再反复解码全尺寸大图。开场 720ms 从纵深位置归位。
  **书架是页头，下面仍是正常网格 / 列表**；历史页保持普通列表。标题右侧的按钮可切回
  纯列表，选择会记住
- 🎞️ **动效** — 取自参考项目的运动语言：按下 120ms 下沉 / 松开 420ms 带回弹
  （cubic-bezier `.2,1.08,.34,1`）+ 轻触感。**进入详情不是淡入，也不是整页横滑**：新页是一张
  纸，被一条斜边（倾角 = 页宽 46%，上限 180dp）从左侧**切进来**，同时从 24dp 左 / 12dp 下的位置
  归位，曲线 `cubic-bezier(.18,.72,.18,1)`，全程不透明；旧页只向右让 10dp 并留在下面被盖住。
  没有交叉淡化 → 文字在每一帧都是实的，也没有两层半透明页面叠加的糊感。路由的 `enterTransition`
  故意是零位移、只占同一个 420ms 时钟，视觉全部由页面内的斜切完成。斜切与位移都在**绘制阶段**
  完成（`clipPath` + `graphicsLayer`），页面只布局一次，420ms 里只重绘不重组。
  其它路由仍是纸张堆叠：进入 420ms 从右滑入，旧页退 1/3（320ms），返回对称；首页报头 / 轮播 /
  快捷入口按 45ms 错峰入场，最多 8 档；全部走 `graphicsLayer`，只重绘不重排
- 🧭 **返回不丢位置** — 首页 / 每周必看 / 分类列表 / 搜索的结果与滚动位置都存在各自
  `NavBackStackEntry` 的 ViewModel 里：点进漫画再返回，仍然停在原来看的那一行，
  不会重新加载并跳回第一屏（每周必看此前正是这个问题）
- 📈 **排行榜** — 每周必看按「周」分组，每颗周标签显示该周日期范围，下方标题同样标出周次，
  不再是一排没有含义的小方块；每页 20 本，滑到底自动续页。另有**日榜 / 周榜 / 月榜**
  （`/categories/filter?time=d|w|m&order_by=mv`），翻页方式相同。每日签到已从首页移到
  「我的 → 每日签到」
- 🎲 **随机一本** — 首页「不知道看什么？交给运气」：随机抽一本直接进详情，
  连点不会连续抽到同一本（走 `/random_recommend`，单次请求）
- 📊 **本地阅读统计** — 「我的 → 阅读统计」：本子数目、累计观看时长、**平均通读率**、
  观看标签占比与「读得最多的」。**只有真正进入阅读器才计入**（点开详情页不算）。
  权重不是页数，而是 `通读率 × 阅读时长 × 评价`：读完 60 页的单行本，比在 900 页长篇里
  翻 20 页更能代表偏好；只扫几秒却停在最后一页也不会被当成读完。数据只存本机，
  不需要账号，可一键清除
- 👍 **三档评价** — 作品详情页底部一行小按钮「起立 / 萎了 / 平平无奇」，随手点、再点取消；
  评价参与上面的权重，标了「萎了」的作品也不会再被「猜你想看」推回来
- 🔮 **猜你想看** — 按本地标签权重抽一个标签，再在该标签的结果里随机一本：
  常看的题材出现概率更高，但不是只看那一类（最多换 3 个标签，抽不到再退回
  `/random_recommend`）
- 🔄 **应用内更新** — 「我的 → 检查更新」读取 GitHub 的 `releases/latest`
  （每次绿构建都会发一个新版本，如 `v1.0.13`，安装包挂在对应 Release 下）：
  直连 `api.github.com` / `github.com` 失败时，自动改用 `github.akams.cn` 收录的反代
  （`gh-proxy.com`、`ghfast.top`、`ghproxy.net`）。下载支持 **Range 断点续传**（中断后只补剩余字节）
  并校验 release 附带的 sha256，然后交给系统安装器。对话框的安装包大小取自 release 元数据，
  下载开始后再由响应头里的 `Content-Length` / `Content-Range` 校正，进度行显示
  **已下载 / 总量**（如 `12.3 MB / 18.9 MB`），换反代或续传后数字不会停在默认值
- 🛰️ **线路与测速** — 「我的 → 线路」同时测接口主机与 GitHub 更新源的真实延迟，
  显示毫秒数；点一行立即生效（接口换主机、更新换直连/反代），自动模式按顺序尝试。
  桌面端在「设置 → 线路」有同一套
- 🎬 **其它内容** — 小说、影片、游戏、部落格、论坛
- 🖥️ **Windows 桌面版** — 独立的 `:desktop` 模块，用 Compose Multiplatform 复用同一套协议实现与
  「墨与火」设计：同款封面网格、无边框卡片、琥珀强调色，首页同样有 **随机一本 / 猜你想看 /
  阅读统计**（统计规则与手机端一致：只在阅读器里按已读页数计权），设置里可打开阅读统计与
  GitHub 发布页（含反代入口）。用 JDK 自带 `jpackage` 打出 **MSI + EXE 安装向导**
  （内嵌运行时，目标机无需装 Java）。范围与验收标准见
  [SCOPE_AND_ACCEPTANCE.md](SCOPE_AND_ACCEPTANCE.md)
- 🎨 **Material 3 Expressive 设计令牌** — 排版 / 形状 / 间距 / 动效四组令牌集中在 `ui/theme/`，
  全应用换肤只需改根上的 `MaterialTheme(...)`；设计稿见 `design/m3e/`

**无广告** — 整个广告与充值 / 金币购买层均已移除。

## 构建

环境要求：JDK 17+，Android SDK（API 36）。

```bash
# 指向你的 Android SDK
echo "sdk.dir=C:\\path\\to\\Android\\Sdk" > local.properties

# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

### Windows 桌面版

同一个仓库里的 `:desktop` 模块是**独立的 Gradle 模块**——`:app` 保持纯 Android 模块，
桌面端的任何改动都不可能弄坏 Android 构建。打包只依赖 JDK 自带的 `jpackage`，
Windows 上还需要 [WiX Toolset](https://wixtoolset.org/)（`jpackage` 的 MSI / EXE 后端）。

```bash
# 直接运行（开发用）
gradlew.bat :desktop:run

# 纯逻辑单测
gradlew.bat :desktop:test

# 打安装包
gradlew.bat :desktop:packageMsi :desktop:packageExe
```

输出：`desktop/build/compose/binaries/main/{msi,exe}/`

### 持续集成

推送或发起 PR 时，两个工作流并行触发，都可在该次 run 的 **Artifacts** 区下载：

| 工作流 | 运行环境 | 产物 |
| --- | --- | --- |
| `.github/workflows/android.yml` | ubuntu-latest | `jm-reader-apk`：debug APK 始终产出；配置签名 secrets 后额外产出 release APK |
| `.github/workflows/desktop-windows.yml` | windows-latest | `jm-reader-windows-installer`：MSI + EXE |

release APK 需要以下仓库 secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、
`KEY_PASSWORD`。未配置时 release 步骤会**跳过**（不是失败），debug APK 照常产出。

Release 构建使用本地 `jmreader.keystore` 签名（见 `app/build.gradle.kts`）；
对外分发时请自行生成密钥库。安装包未做代码签名，Windows SmartScreen 会提示「未知发布者」。

## 项目结构

```plaintext
app/src/main/kotlin/com/jm/reader/
├─ data/
│  ├─ net/         Crypto (md5/AES)、ApiClient (签名+解密)、HostManager (主机引导+探测)、
│  │               ApiError（服务端 errorMsg → 可读文案）
│  ├─ session/     SessionManager (jwt / AVS / 会员信息 / 主机缓存 / 语言 / 版本)
│  ├─ model/       JSON 辅助 + 领域模型 + DailyModels（签到记录解析）、
│  │               CommentItem（评论 / 楼中楼按 parent_CID 线程化，buildCommentThreads）
│  ├─ history/     HistoryManager（本地浏览历史 + 阅读进度，SharedPreferences）
│  ├─ repo/        AppRepository（类型化接口封装，不含广告/金币接口）
│  └─ download/    DownloadManager（并发下载到 Download/JMReader，离线索引，
│                  页面以 .jm 后缀命名以免被相册收录）
├─ ui/
│  ├─ strings/     AppStrings（简中 / 繁中 / 英文）、UiLanguage、LanguageManager
│  ├─ theme/       Color / Theme / Glass（渐变背景 + 玻璃面板与模糊 + 按压反馈）、
│  │               GlassSpec（规范尺寸 / 圆角 / 弹簧 + AppMotion / AppSpacing）、
│  │               Type（M3 Expressive 排版阶梯）、Shapes（Expressive 形状阶梯）
│  └─ splash/ home/ detail/ reader/ download/ category/ search/
│     library/ member/ week/ daily/ novels/ movies/ games/ blogs/ forum/
└─ util/           ImageDescrambler、ReaderImageLoader (LRU 缓存)、CrashHandler、
                   JmId（自适应识别 JM 号 + 展示用 JMxxxx）、HtmlText（评论/论坛去标签）、
                   Versions（版本比较）

desktop/src/main/kotlin/com/jm/reader/desktop/
├─ core/           Crypto / ApiClient / HostManager / Session（java.util.prefs）/
│                  Repository / ImageLoader（Skia 解码 + 去打乱）/ Utils / Models / Strings
├─ ui/             Theme / Glass / Components / App（导航栈）/ Splash / Home / Search /
│                  Detail / Reader / Settings
└─ Main.kt         application { Window(...) }

design/m3e/        jmreader-ui.json（m3e-canvas 设计稿，5 个界面 / 29 组件组）

SCOPE_AND_ACCEPTANCE.md   三项需求的实现范围与验收标准
```

单元测试（`app/src/test/`）覆盖纯逻辑：JM 号识别与展示、签到记录解析（用真实抓取的
`daily_sample.json`）、错误文案映射、三语字符串与格式占位符、版本比较、评论解析与楼层折叠
（含父评论缺失、父子成环等畸形数据）、玻璃规范的尺寸/对比度令牌、M3 Expressive 主题令牌
（排版阶梯单调性与字重、形状与间距尺度、动效弹簧的阻尼约束）。

```bash
gradlew.bat :app:testDebugUnitTest
```

桌面端另有一套等价的纯逻辑测试（`desktop/src/test/`），覆盖 JM 号、去乱切条几何、
Base64 容错、AES 往返、MD5 向量与三语字符串表完整性：

```bash
gradlew.bat :desktop:test
```

## 实现原理（概览）

- **主机发现** — 启动时从 CDN 拉取加密的服务器列表（文件带 UTF-8 BOM，解码时会先剔除），
  解密后依次用 `GET /setting` **探测**候选主机，取第一个真正应答 `code:200` 的地址并缓存；
  内置兜底列表已同步为真实的 API 主机，避免回落到图片 CDN
- **请求签名** — 每个请求携带由当前时间 + 静态密钥推导的 `Tokenparam` / `Token` 请求头
  （与参考客户端 `jmcomic.JmCryptoTool` 一致，版本号跟随 `/setting` 的 `jm3_version`）
- **响应加密** — API 响应为 AES-256-ECB 加密，密钥 = `md5("<ts><secret>")`，
  且必须使用**本次请求**发送的时间戳；应用先解密再解析
- **错误处理** — 信封里的 `code != 200` 与 HTTP 4xx/5xx 统一走 `ApiError`，
  优先展示服务端 `errorMsg`，否则给出本地化文案，界面上不会只出现裸数字
- **图片去打乱** — 阅读器根据 `md5(专辑ID + 页码)` 计算确定的切片数，再重组倒序的水平条带
  （`util/ImageDescrambler.kt`）
- **云端观看记录** — `GET /watch_list?page=`（参考客户端 `GetHistoryReq2` → `ParseHistoryReq2`），
  解密后为 `{"list": [<作品>...], "total": <int>}`，作品结构与 `/latest` 完全一致，直接复用
  `ComicListItem` 解析。写入走 `POST /watch_list {id}`，已登录时阅读章节会自动记录；
  未登录读取实测返回 `401「Authentication fail.」`
- **评论接口** — 与参考客户端 JMComic-qt 的 `GetCommentReq2` 一致：
  `GET /forum?mode=manhua&aid=<作品ID>&page=<页>`（实测返回 `{"total":"23","list":[...]}`，每页 10 条）。
  注意该接口返回的是**扁平列表**，楼中楼通过每条的 `parent_CID` 回指父评论（不是嵌套的 `replys`），
  客户端按此把一页评论重新折叠成楼层树；发评论走 `POST /comment {comment, aid[, comment_id]}`
  （实测该移动端路由可用：未登录 POST 返回 `401「請先登入會員」`，而不是像不存在的路径那样
  返回 `Not legal.<path>`）

## 协议

[GNU General Public License v3.0](LICENSE) — 详见 `LICENSE` 文件。

Copyright © 2026 — 欢迎贡献。
