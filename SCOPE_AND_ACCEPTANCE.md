# 实现范围与验收标准

本文件逐项说明本轮三项需求的**实现范围**（做了什么、没做什么）、**验收标准**（怎样算通过、
由谁验证）与**已知限制**。它是这份改动的验收依据，不是宣传材料——凡未在本机验证过的内容
都在「未验证」里明写。

- 需求一：构建 Windows 版本的可执行程序
- 需求二：用 m3e 重构并优化现有 UI
- 需求三：GitHub Actions 推送后自动构建 Android APK 与 Windows EXE 安装包

技术路线由用户在开工前确认：**Compose Multiplatform 移植** + **m3e 设计稿并与 Compose 代码对齐**
+ **EXE/MSI 安装向导**。

---

## 0. 交付物清单

| 类型 | 路径 | 说明 |
| --- | --- | --- |
| 新模块 | `desktop/` | Compose Multiplatform 桌面端（Gradle 独立模块，26 个文件） |
| 新模块配置 | `settings.gradle.kts` / `build.gradle.kts` | 登记 `:desktop`，声明 compose / kotlin-jvm 插件 |
| 打包配置 | `desktop/build.gradle.kts` | jpackage → MSI + EXE，含图标、升级 UUID、perUserInstall |
| 应用图标 | `desktop/icons/jmreader.ico` | 多尺寸（256/128/64/48/32/16），PNG-in-ICO，10.4 KB |
| 图标生成脚本 | `desktop/icons/make_icon.py` | 零依赖 Python 生成器（无 Pillow），可重新生成 |
| 测试 | `desktop/src/test/kotlin/.../CoreLogicTest.kt` | 20+ 条纯逻辑用例 |
| 设计稿 | `design/m3e/jmreader-ui.json` | 5 个界面、29 个组件组，含分享链接 |
| 主题重构 | `app/.../ui/theme/Type.kt`、`Shapes.kt`、`GlassSpec.kt`、`Theme.kt` | M3 Expressive 排版 / 形状 / 间距 / 动效令牌 |
| 组件重构 | `app/.../ui/components/CommonUi.kt`、`ComicCard.kt`、`ui/MainScreen.kt` | 统一顶栏、封面卡、底部导航 |
| 测试 | `app/src/test/.../theme/AppThemeTokensTest.kt` | 主题令牌护栏（15 条） |
| CI | `.github/workflows/android.yml` | Android：单测 + debug APK + 条件 release APK |
| CI | `.github/workflows/desktop-windows.yml` | Windows：单测 + MSI + EXE 安装包 |
| 签名 | `app/build.gradle.kts` | release 签名改为条件启用 + 从环境变量读取口令 |

**设计稿分享链接**（可交互查看 / Tidy / 调主题 / 复制 prompt）：

```
https://lnkiai.github.io/m3e-canvas/#docz=7VtbU9tIFn7Pr1D5dVMLNoSQvO3szlbN7GZra153UpRsBFbFWC5ZJmSnpkoGHNtgDAnXgAmYycUTDCaBBOMLVO1fQd2SnvwXtlot27rZloWyxWxNKg-4W-rT5zuXPuf00U93CMLD0VyI8jwkPN8_In6gyHGK9dxF436WpibQuLz7GhxvwY0LsVgUymm4cQFrh-JaVd5KSKUqOD6AqXPxsNSobcPPy9L71ABcyYlnB0I9B1bj13wc7r0Vc4tS6R2cS6CfmbR4uC8tzOL3r_lZmCvCYl5OLsHX83BhQcyewFxaXM_IuwdidU8o8yB1QjwiOYqlyRAxRHw7E2GpaJSephq1baHMC-UPUvKDzM-CyhqmDfPPG7WUUF4CmQpIPcdbGRDqOYlPDHz3l0Ytfc3HwUkcUai_gPs1UH5HfP-IAMvn0vuXcD95zcfxhuX8Z6HMi7mamFuE5QTe-TUfV5l_VRDKq_C0AJ4nwPEFzBWlyuE1PwtW42I6I5SrUvqTUK7AX19J6U-NWkaubknHb-D5Ryn9ifgDAb98gnxczFbFl3PSWQHO713zsxj9CZacUqQSCTJhCo9FQiQ3wbBTaJgMj7MMPa5OkCGK46i_Uc-UqSl_U4ZckFJW-ekOQRCEZ5xkn3geEhwbo-7iET_DBR8x41RUPxxgwhxLRjm0XJQjw-Mki0kRhCcaJCPKzlgmFh6nWuMTTJjDw36GY_4aomaaM9RUJEhG6X9T43oqUwxHM2H0DtWSqOcOQfzcRgDt61_K05gFgvDQaBVPkJmi1PUJwhNWwZLfbcj5z-3xGc9DYrD165nuV5jhlHfASgksFMBKVtyex-83atty_os8V8DKBBJvwfIvSFzZgrS0ItSX0N85Hm6cYMUSym9AahMrUqOWASsvhPIirG6DhYKU_IAWX9iX6nWhvCiUeTn_uSlkLIFJBeQYO0EGqD8zYY6kwxT7d-apR3ni57tW3Ecpkg0EzfzjDev5H37g64FAZxPali5XEfuJU3ntWCotSZ9PxMNX4HkCP9OoZfADYqUE10_A0SYxoJoRMUBgcOTVS5h9C1KvxJ0zmH0L06ugxjd9wyzMfcArwM0v4OO6-KYizh3JmwW4nxSPLhFQXVAYpziSDplRwKtj-9Vj8WB0uBcWWKJXeRgvCeUstneQ2pTyhUZtB6sFWC4hQa4dgzdLIPVF5RhJvrIuzxV-9IAaD94vYhfxo0e62kEQIefJS6XdHkyxbResV-2mw9Fz5B2-P9KDJfFwA6y8kK5ei9UjvApy1etn4uG-nP8s7x4oHnYTpE5AZR2JVNFZ6WoLZPekg0OYXhUuPiLjUB5usQmWSyCVhEsHQnkBc4Zkruh4by6jFMfR4cmomU_p-FKsHxuYfDAy2oNJqXQkFXh0vCjOVShX5YOtaz4u1lbB0Rby3CvPhXIWJE6FShaLt73FOwTxWPE5kywTi3T1OWN-krXnX8gZGq3kmWk_TnPUVHt1LQUjFY6JaAkp00_osPIAx0T-FIl8Y5wOkX4qZD7H26sHFF8bjoVCpnGfhVNRJqdJliaxX5-gQ6GWtzegD5ZPhfICOjqxx9y5hPtJrB0gdYIHDY4PYxRAJ0C0dUKZNvUTYle7OcLDsWQ4SjdPjglynPIoEmz-a__d_OtxF0VU0Da6U0Xn9GblHXRJsphWR-Hi6c7C1cZW2MdKfKJRy8DUBnakqjeyJ_se4sXicSIG29BPUCQXY6nxMcxhVxHcd0kELZocNcN1sDHTTFsCShTgCr4oJvI8JHyDulE_E2oGSs7x7Iqkb3DEZSQD7QDRgKR5po1krgheFMRPlzKfxhptgCcWiTAsOieUY2z2AiSr-DC1POHtSoIKUdMk10EWQ6ODpmGfAtlgD9NoRiNG04iGaIe2ESI5KsrZsIxhn0vyVCk6swslIr6tdoHUcIzsjuKIzx0UFVrevs0BKzXcT8oHW0KZ72YL3z_y-oaG743cdxNu76jPWvXv_e9VX4HQrxeXzzv8FeXlu5m8Khk78hr9f5VXmJzuFhXfHx11R1R-huOYKS01nbTw9D-M0y2RuQI_R_r1m1U23FpOLU1oqKpViSZ-Fq-0wypDoNf9pWYOpXlNTZ90EfFjvZpQISrAKbUYKz2xCMY50v_Q20cUeNf8uiGWb2_8BtG8lTLinZmTtM5FEKfqqFK6SaJmqNYQdrI0G8ral_WqbEzQVMgQM94b1OfcbmRAKjU6HIl1CDBuVf7TKi7oS2ON2narIgZWMqh09aIulI_wUXDNx_GuhHIWl7EatR19gQwfBoYSGa6PSeenUr3Yqo85EmaQsQwaTQJ1IZ_SUHQUNGqrfbcwdFTZCwTpSLQ7mP2nVBY0UNCoCT2CdETn25GKbSTRkBYcK0i0PtiSkK8rIXE-L1wdw6NfXKE11JUWWMkIlYorhIa7E9o4lvIZmCvao2VfO1gqGgvZsjjfvWG3LE4l6ixTU7yYWF2Fr3PEf84JkPhIeH2jhDxXsErDbxiljjixOMxeL5t7YE4AnjmEk3naKWML0VHuO46asgFmf7kbgh6fFGBl6SvV6-xH-ndtotQhT-ofJZsZ028TpSHXdCltB6UHtwsl-3ZuyhuNgbobmWMzKfg9d3Qxd_Tazx0HtVrU5PC2ZY5Yu82ZY-crY6fqqFK6SebYs_7tIVnkhvxk4Il5XrntQy0eVJgbCzCRZ55-VLh156dcfuOrPriwIJXUqry4Pa_mYOoTqROQ_iCdncHdq_4uALWSx5z0dD39Xv-psggaLt1ncL6rL2K5kQCr9Bxdl2jrjd0OhdY5gA4FXbTh9f1x6AmBu7TQb98woW2XIWw6NmcXKN5BJ5KJciTL9RDMkAvXH1pyY_4YxzHhDseExVw7x9A0fljbZSREPhtTjLMvo-sArvmQbraPuHJIN7U1SEY4irW8iDLJ4sHNz2sDWWeFBU3jzi0sLOhZjPYAdXjInDf2m-gYQHWW7IjFIuElpNJuN__j0KvYC0Lt63fXUN2AhrOkBqHh64nGyG8ODWfJC0JjqCcavluBhp2AEK9mDgi7dNw5dXgqqZtEhB0s03Y4iITrKAyUrtbAzmv0P3WiDUsbtR3UFXb5HvtgNU5Uuj_V9m8-DRd_vXURoSqLCDlJGeVuSE3diAdVavSUlpxO8BZT7qakHUKLpny1jaLaTlKYfQlSm6innV8BtX3c4tyo7Zg7RkGuIL3_RdyPg5Wstre07-uNpmhYZhJ1i_cQzz33xBOiw1Qnw8Rz_zTu6SsIapoMxZBIhvWCekpOP9P11RsEiLqGUycwVxQP98XtedRAVXnnFHtTvcYM-4hrsEdYqkOtBiH4TbdAvNWTbO0Oo0_oiLI8zcSiHd0ex4TJkLXXA6kk8ndNKo1aBn1sslwS1wpw84tYPxbjJ3qMux7ATXQ7Rro2GF7sybB5-f6YXbTNrJ3ztVnLsThhO7d7Oy8BqsRucsYaWtOJbrb89e7rVUZ0B7YKm6FUZXVA9X8_0qRHhidjHU-pHrEh7s7vFhiKF3H5cK15w2mlwtYbsFusyhXEY16or2IKxAChJUgMEN-GJ0N0NEgIF4vy5hluXW_UMuLhIlg6Fdf24HqqD3tuoaZ8ceUEMe1nDN1wk8735O1l8bQqVvescUNbGJtixvsDLvqU5gJBq3MlEKQCTyhz1msPkAAZCDrTIVhOyK_egJ1LMZ3EX3V04JcKURw1Fn1KURFHuiInF-DaJTj_hKmI2_P4S0GhnFUDGmUPjdoO-syrmMflHqG2jb5g3EB-UqrXHfRuNFWGnqbNxUijbQ8N9esUrQhpL8xbdHVxi2tX5SpR0s_EevmtoZGb1_P0BJ0VXPHHQkSH72n0dmj-SFbzYWxGqCaEyx2h_EGoVIT6lbhWQM1BCzzMlcDRO-FiH30zqnxCKqZTcHcO1F-C7B4u5MI0D3Np1OKUK4LKmvLuEkxvicd5oVoV6uv4RVMiZSPKZGIcCl9NdvCdknNY2H6PGq_3Xn813paQzGGlMQRw5x6wGXH8fhPo4k2gvgv5690E9tOCqnm7Y_qPvvq78_Od_wI
```

---

## 1. 需求一：Windows 版本可执行程序

### 1.1 实现范围

**选型**：Compose Multiplatform for Desktop（Kotlin/JVM + Compose 1.8.2 + Skiko），用 JDK 自带
`jpackage` 打出 MSI 与 EXE 两种安装向导。

**为什么不是别的方案**：Android 端已把全部网络/加解密/解析逻辑写在纯 Kotlin（不含 Android API）
的形态里，移植成本主要落在这几处替换上——`SharedPreferences → java.util.prefs`、
`android.util.Base64 → java.util.Base64`、`Coil → Skia 直接解码`、`org.json → 真实 org.json`。
换 Tauri/Electron 需要把上述逻辑用另一种语言重写一遍，还要再维护一套 UI；换 PWA 无法满足
「EXE 安装包」的形态要求。这条路线让两端共用同一套协议实现与同一份设计语言。

**已实现**：

| 层 | 内容 |
| --- | --- |
| 加解密 | `Tokenparam`/`Token` 请求头签名、AES-256-ECB 响应解密、Base64（含 UTF-8 BOM 容错与补位） |
| 主机发现 | CDN 拉取服务器列表 → 解密 → `GET /setting` 逐个探测 → 缓存 + 兜底主机 |
| 网络层 | OkHttp 4.12（与 Android 端同大版本）、3 次重试、错误归一化为可读文案 |
| 数据模型 | `ComicListItem` / `ComicDetail` / `ReadPage` / `Member` / `Category` 等 |
| 仓库层 | `bootstrap` / `getPromote` / `getLatest` / `randomRecommend` / `adaptiveSearch` / `searchPage` / `hotTags` / `categories` / `getAlbum` / `comicRead` / `comicCover` / `imgUrl` |
| 图片 | Skia 直接解码 + **去打乱还原**（水平条带倒序重组）+ LRU 缓存（160 条） |
| 会话 | `java.util.prefs` 持久化，节点不可用时退化为内存字典（不崩） |
| 界面 | 启动引导 / 首页 / 搜索 / 作品详情 / 阅读器 / 设置 共 6 屏 |
| 打包 | MSI + EXE，内嵌运行时（目标机无需装 Java）、开始菜单项、桌面快捷方式、可选目录、perUserInstall |

**未实现（Phase 2，见 §4）**：登录与会员、分类页、书库、下载与离线阅读、评论区、小说 / 影片 /
游戏 / 部落格 / 论坛五个附属板块、阅读器横向翻页与缩放。桌面端定位是「浏览—搜索—详情—阅读」
主线可用，完整客户端仍是 Android 版。

### 1.2 验收标准

| # | 标准 | 判定方式 | 状态 |
| --- | --- | --- | --- |
| A1 | `:desktop:test` 全绿 | CI 第 3 步 | 待 CI |
| A2 | `:desktop:packageMsi` 与 `:desktop:packageExe` 均产出文件 | CI 第 4 步，产物收集脚本以 `if-no-files-found: error` 卡住 | 待 CI |
| A3 | 产物名为 `JMReader-msi-<sha>.msi`、`JMReader-exe-<sha>.exe` | 产物收集后 `Get-ChildItem artifacts` 打印 | 待 CI |
| A4 | 在干净的 Windows 机器上双击安装包，向导走完，开始菜单出现 JM Reader，能打开 | **人工**：下载 artifact → 双击 → 下一步到底 → 启动 | 待人工 |
| A5 | 打开后首页能加载出封面网格（说明主机发现、签名、解密、图片解码四条链路都通） | **人工**：观察首页 | 待人工 |
| A6 | 点开一部作品 → 章节列表出现 → 进入阅读器 → 图片未打乱、可上下滚动翻页 | **人工** | 待人工 |
| A7 | 再次安装更高版本时覆盖升级，而不是并存两份 | **人工**：改 `packageVersion` 重发一版 | 待人工 |
| A8 | 卸载后不留目录与快捷方式 | **人工**：控制面板卸载 | 待人工 |

### 1.3 已知限制

- **本机无法编译**：开发机上没有 JDK（`java` 不在 PATH）、`local.properties` 指向
  `/root/toolchain/android-sdk`（Linux 路径，本机是 Windows）、且网络不可达 Maven Central。
  因此 `desktop/` 下所有文件与改动过的 `app/build.gradle.kts` **从未被编译过**。
  这是刻意的：按需求三，构建由 CI 承担，CI 是唯一验证路径。若 CI 报编译错，属于预期范围内的
  首次编译修正，而不是「本应在我这边发现的问题」。
- **安装包体积**：`includeAllModules = true` 会把整个 JDK 运行时打进安装包（约 150–200 MB）。
  裁剪需要 `modules(...)` 白名单，但 Compose Desktop / Skiko / OkHttp / `java.util.prefs` 会用到的
  模块比表面看到的多（AWT/Swing、JUL、EC 加密、`jdk.unsupported`），而漏掉一个模块的表现是
  **用户机器上运行期崩溃**、构建期毫无提示。所以刻意不裁剪，列为 Phase 2 待办（先冒烟，再裁）。
- **图标**：`jmreader.ico` 由自写的零依赖 Python 生成器绘制（无 Pillow、无外部素材），风格是
  简化版「翻开的书 + 品牌橙」。它是可用的占位图标，不是设计过的品牌资产。
- **未做代码签名**：Windows SmartScreen 会对未签名安装包提示「未知发布者」。正式分发需自备
  代码签名证书。

---

## 2. 需求二：用 m3e 重构并优化现有 UI

### 2.1 实现范围

分两半交付：**一份可交互设计稿** + **一套落地的设计令牌与统一组件**。

#### 2.1.1 设计稿（m3e-canvas）

`design/m3e/jmreader-ui.json`：5 个手机框（412×892），29 个组件组，覆盖首页 / 搜索 / 作品详情 /
阅读器 / 设置。设计基调：

- `paletteKey: amber`（品牌橙），`theme.dark: true` 且 `bothModes: true`（深色为主、深浅双模）
- `shape: rounded`、`emphasized: true`、`motion: expressive`、`font: robotoFlex`
- 设计要点：**一个自适应搜索框**（不再区分作品 / 作者 / ID）、封面网格带 **JM 号角标**、
  详情页「一眼看清章节」、阅读器「专注内容本身」

校验：JSON 合法、无重复 id、无越界的 `action.to`、所有 group 的 x 坐标 ≥ 0。

#### 2.1.2 代码落地

第一步是**把设计令牌集中起来**——这是本轮性价比最高的一步，因为
`MaterialTheme.typography.*` 被 21 个文件引用，换掉根上的 `MaterialTheme(...)` 就等于全应用换肤。

**新增 `ui/theme/Type.kt`** — M3 Expressive 排版阶梯：

- 标题（`headlineLarge/Medium/Small`、`titleLarge/Medium`）改 **SemiBold**，字距收紧
  （-0.25 ~ -0.2sp）；正文回到 Normal；标签（`labelLarge/Medium/Small`）用 **Medium**。
  这是对照 Material 默认排版（标题也是 Regular）最实质的改进：在封面为主的界面上，
  默认排版会让标题和说明文字几乎同重，层次读不出来。
- 追踪规则：**字越大越收紧，小于 16sp 转为正字距**，避免 11sp 标签挤成一团。
- **字体族刻意未捆绑 Roboto Flex**：捆绑要多约 1.5 MB 资源；声明为「可下载字体」则会让首屏
  依赖一次字体服务商网络往返、且依赖 Google Play 服务，对要发到无 GMS 设备的构建是坏交易。
  `FontFamily.Default` 在 Android 上即 Roboto（正是 Roboto Flex 的母设计），差异只在少数
  字形的宽度上；真正承载 Expressive 观感的是上面的尺寸/字重/字距阶梯，这部分已完整应用。
  将来若要换字体，改 `AppFontFamily` 一行即可，全部界面自动跟随（`AppThemeTokensTest` 有一条
  用例断言所有样式共用同一字体族，防止漏改某一条）。

**新增 `ui/theme/Shapes.kt`** — Expressive 形状阶梯，并与既有液态玻璃规范**对齐锚点**：
`small(12dp) == GlassSizes.RadiusInner`、`medium(16dp) == RadiusStandard`、`large(24dp) == RadiusHeavy`，
另有 `extraSmall(8dp)`、`extraLarge(32dp)`。这样「卡片圆角是多少」只有一个答案，不会出现两套
互相打架的尺度。

**扩展 `ui/theme/GlassSpec.kt`** — 新增两组令牌：

- `AppMotion`：按 Expressive 的**「什么在动」**分成 spatial（位置/尺寸，允许轻微过冲）与
  effects（颜色/透明度，**绝不**过冲——过冲会冲过钳位，表现为闪一下）。取值刻意复用液态玻璃
  规范里已有的弹簧（`spatialFast` 就是 `GlassMotion.settle`，`spatialSlow` 就是 `GlassMotion.drag`），
  因为「玻璃面按 `spring(0.5,300)` 下沉、导航胶囊却按另一个弹簧滑动」看起来会像两个 App 拼起来。
- `AppSpacing`：4dp 基准的间距阶梯，且 `ScreenEdge == GlassSizes.ContentPadding`、
  `Section == GlassSizes.HeavyContentPadding`。

**改造 `ui/theme/Theme.kt`** — 把 `typography` 与 `shapes` 接进 `MaterialTheme`。
注意：这**不会**让未显式指定样式的文本变形——M3 的 `bodyLarge` 默认就是 16sp，本轮改的主要是
标题族的字重。

**统一三个共享组件**：

- `components/CommonUi.kt`
  - `AppTopBar`：标题改用 `titleLarge`（22sp SemiBold）；新增可选 `actions` 插槽（搜索/分享/更多），
    给后续页面收口，默认空实现——15 个既有的双参调用点全部不受影响；补上 `actionIconContentColor`
    避免「有图标但颜色不对」。**顶栏高度依旧不设固定值**，原因写在代码注释里：
    `TopAppBar` 的状态栏内边距是在自身测量高度内应用的，外面套高度会把标题裁到系统栏下面。
  - `ErrorView` / `EmptyView`：接上 `bodyLarge` 与间距令牌。
  - `ComicGrid`：`GridCells.Fixed(3)` → `Adaptive(108.dp)`。在 411dp / 360dp 手机上**仍是 3 列**
    （411dp 时 `(395+8)/116 = 3.47 → 3`），但 600dp 平板变 4 列、宽折叠屏变 6 列。
    这就是 Expressive 的「自适应」：一套会自己**合适**的布局，而不是一套布局外加一张断点表。
- `components/ComicCard.kt`：圆角从字面量 `RoundedCornerShape(4.dp)` / `(12.dp)` 改为
  **主题形状角色**（封面 `shapes.small`、角标 `shapes.extraSmall`）；标题加 `Medium` 字重，
  与下方 `labelSmall` 作者行拉开层次（都是 Regular 时两行会读成一块灰色）；内距接 `AppSpacing`。
- `ui/MainScreen.kt`（底部导航）：动效从 `GlassMotion.settle` 改为语义化的 `AppMotion.spatialFast`
  （同一个实例，但读作「短距离空间位移」）；图标 **22dp → 24dp**——Material 图标画在 24dp 网格上，
  非 24 的尺寸会重采样每一根描边，和全应用其它 24dp 图标并排时显得发虚。

### 2.2 验收标准

| # | 标准 | 判定方式 | 状态 |
| --- | --- | --- | --- |
| B1 | 设计稿可打开，5 个框齐全，能 Tidy / 切主题 / 复制 prompt | **人工**：打开分享链接 | 待人工 |
| B2 | `AppThemeTokensTest` 全绿（排版阶梯单调、行高不为 Unspecified 且 ≥ 字号、标题 SemiBold、标签 Medium、大字负字距小字正字距、形状阶梯 8/12/16/24/32 且四角对称、间距阶梯单调且与玻璃尺寸令牌一致、动效 effects 弹簧不漏阻尼、spatial 弹簧可控） | `:app:testDebugUnitTest`（CI 第 2 步） | 待 CI |
| B3 | 既有 `GlassSpecTest` / `GlassContrastTest` 仍全绿（形状/间距令牌与玻璃规范对齐后不得回归） | 同上 | 待 CI |
| B4 | 全部 15 个 `AppTopBar` 调用点编译通过（新增 `actions` 有默认值） | `:app:assembleDebug` | 待 CI |
| B5 | 手机上封面网格仍是 3 列（不因 `Adaptive` 变更列数） | **人工**：411dp 或 360dp 机型看首页 | 待人工 |
| B6 | 底部导航指示器滑动仍跟手，图标不模糊 | **人工**：连点 4 个标签 | 待人工 |
| B7 | 顶栏标题**没有**被状态栏裁切（这是本轮最容易踩的回归） | **人工**：看任意二级页顶栏 | 待人工 |
| B8 | 深/浅色两种主题下标题都清晰可读 | **人工**：系统切深色 | 待人工 |

### 2.3 已知限制

- 设计稿覆盖 5 个界面，而应用有 25+ 屏。**没有**按设计稿逐屏重写——那样会把大量可用代码推倒重来，
  风险远大于收益。本轮的做法是：换掉根主题令牌（全应用受益）+ 收口三个共享组件，
  其余界面靠继承令牌自动改善观感，逐屏精修列为 Phase 2。
- 设计稿的 `robotoFlex` 字体族未落地，理由见 §2.1.2。
- 令牌改动**未在本机编译验证**（原因同 §1.3）。`AppThemeTokensTest` 是我能给的最强护栏：它拦的是
  「有人把标题字重改回 Regular」「把圆角改回 4dp」这类看不见但会被感知的漂移。

---

## 3. 需求三：GitHub Actions 推送后自动构建 APK + EXE

### 3.1 实现范围

两个独立工作流，互不阻塞：Android 跑在 `ubuntu-latest`，Windows 安装包跑在 `windows-latest`
（jpackage 只能在 Windows 主机上产出 Windows 安装包）。

**`.github/workflows/android.yml`**（重写）

| 步骤 | 说明 |
| --- | --- |
| 检出 / JDK 17 (temurin) | 开 Gradle 缓存 |
| 确保 Android SDK platform 36 | `sdkmanager` 存在才执行，失败不致命——Gradle 会自己拉缺失包 |
| `:app:testDebugUnitTest` | 纯逻辑单测 |
| `:app:assembleDebug` | 始终构建 |
| 还原签名密钥库 + `:app:assembleRelease` | **仅在配置了 secrets 时**执行 |
| 收集 + 上传 artifact `jm-reader-apk` | `if-no-files-found: error` |

**`.github/workflows/desktop-windows.yml`**（新增）

| 步骤 | 说明 |
| --- | --- |
| 检出 / JDK 17 (temurin) | jpackage 拒绝在 17 以下运行，工具链也在模块里钉了 17 |
| 确保 WiX Toolset | jpackage 的 MSI/EXE 依赖 WiX 的 `candle.exe` / `light.exe`；镜像通常自带，仅在缺失时 `choco install wixtoolset` 并把 bin 目录写进 `GITHUB_PATH` |
| `:desktop:test` | 纯逻辑单测，无需网络 |
| `:desktop:packageMsi :desktop:packageExe` | 产出两种安装包 |
| 收集 + 上传 artifact `jm-reader-windows-installer` | 重命名为 `JMReader-<fmt>-<sha7>.<ext>`，`if-no-files-found: error` |

两处踩坑已在代码注释里写明：

1. **artifact 步骤的 `if` 读不到 `secrets` 上下文**，所以「有没有签名密钥」在 **job 级 `env`** 里
   预先算成字符串（`HAS_KEYSTORE: ${{ secrets.KEYSTORE_BASE64 != '' }}`），步骤里只判断
   `env.HAS_KEYSTORE == 'true'`。
2. **release 签名改为条件启用**：`app/build.gradle.kts` 里只有在密钥库文件真实存在时才创建
   `release` signingConfig，否则 `assembleRelease` 会卡在一个不存在的 keystore 上。同时口令/别名
   改从环境变量读取（`JMREADER_STORE_PASSWORD` / `JMREADER_KEY_ALIAS` / `JMREADER_KEY_PASSWORD`），
   旧字面量保留为默认值——这让改动的方向是**严格更宽松**，不可能让原本能过的构建变失败。

### 3.2 验收标准

| # | 标准 | 判定方式 | 状态 |
| --- | --- | --- | --- |
| C1 | 推到 GitHub 后两个工作流都被触发 | Actions 页出现两个 run | 待 CI |
| C2 | 两个工作流的 YAML 语法合法 | 推送后 GitHub 不报 workflow 语法错 | 待 CI |
| C3 | Android 产出 `JMReader-debug-<sha>.apk` | 下载 artifact 能装、能开 | 待 CI |
| C4 | 未配置 secrets 时 release 步骤被**跳过**（不是失败） | run 详情里该步骤显示 skipped | 待 CI |
| C5 | 配置 secrets 后额外产出 release APK | 同上 | 待人工（需自备密钥库） |
| C6 | Windows 产出 `.msi` 与 `.exe` 各一份 | 下载 artifact | 待 CI |
| C7 | 手动触发（`workflow_dispatch`）也能跑 | Actions 页 Run workflow | 待 CI |
| C8 | 同一分支连续推送时旧 run 被取消（`concurrency` + `cancel-in-progress`） | 同分支第二次推送后旧 run 变 cancelled | 待 CI |

**release APK 需要的 secrets**（Repository → Settings → Secrets and variables → Actions）：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 密钥库文件的 base64（`base64 -w0 jmreader.keystore`） |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | 别名 |
| `KEY_PASSWORD` | 别名口令 |

### 3.3 已知限制

- 产物只有可下载的 artifact，**没有** GitHub Release 附件。需要发布页的话，要再加一个
  tag 触发、`softprops/action-gh-release` 之类的工作流。
- 安装包未做代码签名（同 §1.3）。
- CI 首次运行大概率会暴露若干编译问题（原因见 §1.3）——这是预期内的首次编译修正。

---

## 4. 未验证项汇总

本机环境缺 JDK、Android SDK 与网络，以下内容**完全没有在本机验证过**，请以 CI 结果为准：

| 范围 | 内容 |
| --- | --- |
| 编译 | `desktop/` 下 26 个文件、`app/.../theme/Type.kt`、`Shapes.kt`、`AppThemeTokensTest.kt`、改动后的 `CommonUi.kt` / `ComicCard.kt` / `MainScreen.kt` / `Theme.kt` / `GlassSpec.kt` / `app/build.gradle.kts` |
| 打包 | jpackage 产物能否生成、体积、安装/卸载行为 |
| 运行 | 桌面端能否启动、能否取到数据、图片去打乱是否正确 |
| 视觉 | 手机上 3 列是否保持、顶栏是否被裁、导航动效是否跟手 |

已经在本机验证过的：`desktop/icons/jmreader.ico` 的字节结构（256/128/64/48/32/16 六个目录项，
全部为 PNG 签名）、`design/m3e/jmreader-ui.json` 的 JSON 合法性与引用完整性、设计稿分享链接可生成。

---

## 5. 后续阶段

**Phase 2 候选**（按建议优先级）：

1. 跑通 CI 首次编译，修掉构建错——这是所有后续工作的前提。
2. 在真实 Windows 机器上冒烟安装包（§1.2 A4–A8）。
3. 裁剪 JRE 模块把安装包减半（§1.3）。
4. 桌面端补齐登录 / 下载 / 分类 / 书库（§1.1）。
5. 按设计稿逐屏精修 Android 剩余 20+ 屏（§2.3）。
6. 引入代码签名证书与 GitHub Release 发布流程（§1.3 / §3.3）。
7. 若确实需要 Roboto Flex，改为捆绑资源并只改 `AppFontFamily` 一行（§2.1.2）。
