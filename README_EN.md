# JM Reader

A native **Kotlin + Jetpack Compose** Android comic reader.

> [!IMPORTANT]
> **Disclaimer** — This is an **unofficial, third-party** client built for personal use and study.
> It is **not affiliated with, endorsed by, or connected to** 18comic / JMComic / 禁漫天堂.
> All comic content displayed by this app belongs to its respective copyright holders and is
> served by third-party sources. The app itself hosts or stores **no content**.
> Please respect copyright and the terms of service of any source you use this with.

[中文 Readme →](README.md)

## Features

- 📖 **Browse comics** — home (promote + latest), categories, weekly ranking, daily check-in,
  hot tags, keyword search, random recommendations
- 🆔 **The work id, everywhere** — every cover carries its `JM` number as a corner badge, and the
  detail page makes it one tap to copy, so a comic is easy to identify, quote or search for
- 💬 **Comments** — the detail page embeds the album's comment thread
  (`GET /forum?mode=manhua&aid=`) with avatar, level, time, likes, and nested replies folded back
  together by `parent_CID` and collapsible; log in to post a comment or reply
  (`POST /comment`), with a clear login prompt when logged out
- 🔎 **Better search** — besides keyword search: **search by author** (results grouped per author)
  and **jump straight to a comic by its JM id**; cards show the author, the detail page links
  authors / tags to a search, and results page in as you scroll
- 📚 **Native reader** — vertical / horizontal paging, chapter switching, progress slider,
  automatic **image de-scrambling** (some album pages are served with reversed horizontal
  strips; this app restores them)
- 🌐 **Trilingual UI** — 简体中文 / 繁體中文 / English, switchable any time
- ⬇️ **One-tap download** — download a whole album (all chapters, all pages) as plain,
  de-scrambled JPEGs into `Downloads/JMReader/<albumId>/` on your device:
  visible in the Downloads folder, transferable via USB, readable by any gallery app,
  and playable in the built-in **offline reader** (no network needed)
- 🕘 **Browsing history** — every album you open is remembered on the device (no account
  needed, works offline), with per-item delete and clear-all
- 🛡️ **Crash report on next launch** — if the app crashes, the stack trace is saved and shown
  on the next launch with a one-tap **copy log** button
- 👤 **Member features** — login / register, favorites, viewing history, daily check-in, profile
- 🪟 **Liquid-glass UI** — built to the [skill-liquid-glass](https://github.com/JUEMING-006/skill-liquid-glass)
  spec (on [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)): the page is
  recorded into a layer and the floating navigation bar genuinely samples and blurs what is behind
  it (2 dp blur + neutral fog + 45° specular + rim light + drop shadow), with content scrolling
  underneath. Sizes and radii come from the spec's token table (64 dp capsule bar / 56 dp tabs,
  48 dp buttons, 16 dp cards) and every glass surface presses with the spec's damped spring instead
  of a ripple. The top bar keeps Material's default height on purpose: `TopAppBar` measures its own
  status-bar inset, so forcing the spec's 56 dp clips the title under the system bar. Light/dark,
  effective on API 31+, degrading to a translucent haze below that
- 🎬 **Secondary content** — novels, movies, games, blogs, forum
- 🖥️ **Windows desktop build** — a separate `:desktop` module (Compose Multiplatform) reusing the
  same protocol implementation and design language: browse / search / detail / read, packaged into
  **MSI + EXE installers** with the JDK's own `jpackage` (runtime bundled, no Java needed on the
  target machine). Scope and acceptance criteria live in
  [SCOPE_AND_ACCEPTANCE.md](SCOPE_AND_ACCEPTANCE.md)
- 🎨 **Material 3 Expressive design tokens** — type / shape / spacing / motion tokens centralised in
  `ui/theme/`, so re-skinning the whole app is a change to the root `MaterialTheme(...)` call;
  the design doc lives in `design/m3e/`

**No ads.** The entire advertising and recharge / coin-purchase layer is intentionally absent.

## Build

Requirements: JDK 17+, Android SDK with API 36.

```bash
# point gradle at your Android SDK
echo "sdk.dir=C:\\path\\to\\Android\\Sdk" > local.properties

# on Windows
gradlew.bat :app:assembleDebug

# on macOS / Linux
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Windows desktop

`desktop/` is a **separate Gradle module** — `:app` stays a pure Android module, so nothing that
happens in the desktop build can break the Android build. Packaging needs nothing beyond the JDK's
own `jpackage`, plus [WiX Toolset](https://wixtoolset.org/) on Windows (jpackage's MSI / EXE backend).

```bash
# run it (development)
gradlew.bat :desktop:run

# pure-logic tests
gradlew.bat :desktop:test

# build the installers
gradlew.bat :desktop:packageMsi :desktop:packageExe
```

Output: `desktop/build/compose/binaries/main/{msi,exe}/`

### Continuous integration

Pushing a branch or opening a PR triggers both workflows in parallel; each artifact is downloadable
from that run's **Artifacts** section:

| Workflow | Runner | Artifact |
| --- | --- | --- |
| `.github/workflows/android.yml` | ubuntu-latest | `jm-reader-apk` — a debug APK always; a release APK too once the signing secrets are configured |
| `.github/workflows/desktop-windows.yml` | windows-latest | `jm-reader-windows-installer` — MSI + EXE |

The release APK needs these repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`. Without them the release steps are **skipped** (not failed) and the debug APK is
still produced.

The release build is signed with a local `jmreader.keystore` (see `app/build.gradle.kts`);
generate your own keystore for distribution. The installers are not code-signed, so Windows
SmartScreen will show an "unknown publisher" prompt.

## Project layout

```plaintext
app/src/main/kotlin/com/jm/reader/
├─ data/
│  ├─ net/        Crypto (md5/AES), ApiClient (signing + decryption), HostManager (bootstrap)
│  ├─ session/    SessionManager (jwt / memberInfo / host cache / language)
│  ├─ model/      JSON helpers + domain models
│  ├─ repo/       AppRepository (typed endpoint methods, no ad/coin methods)
│  └─ download/   DownloadManager (album download → Downloads/JMReader, offline index)
├─ ui/
│  ├─ strings/    AppStrings (zh-CN / zh-TW / en), UiLanguage, LanguageManager
│  ├─ theme/      Color / Theme / Glass, GlassSpec (spec sizes / radii / springs +
│  │              AppMotion / AppSpacing), Type (M3 Expressive type scale),
│  │              Shapes (Expressive shape scale)
│  └─ splash/ home/ detail/ reader/ download/ category/ search/
│     library/ member/ week/ daily/ novels/ movies/ games/ blogs/ forum/
└─ util/          ImageDescrambler, ReaderImageLoader (LRU cache), CrashHandler

desktop/src/main/kotlin/com/jm/reader/desktop/
├─ core/          Crypto / ApiClient / HostManager / Session (java.util.prefs) /
│                 Repository / ImageLoader (Skia decode + de-scramble) / Utils / Models / Strings
├─ ui/            Theme / Glass / Components / App (nav stack) / Splash / Home / Search /
│                 Detail / Reader / Settings
└─ Main.kt        application { Window(...) }

design/m3e/       jmreader-ui.json (m3e-canvas design doc: 5 screens, 29 groups)

SCOPE_AND_ACCEPTANCE.md   implementation scope + acceptance criteria per feature
```

Unit tests (`app/src/test/`) cover pure logic: JM id parsing/display, check-in record parsing
(against a real captured `daily_sample.json`), error-message mapping, the trilingual string tables
and their format placeholders, version comparison, comment parsing and thread folding (including
malformed data: missing parents, parent-child cycles), the liquid-glass size/contrast tokens, and
the M3 Expressive theme tokens (type-scale monotonicity and weights, shape/spacing scales, motion
spring damping constraints).

```bash
gradlew.bat :app:testDebugUnitTest
```

The desktop module has its own equivalent pure-logic suite (`desktop/src/test/`) covering JM ids,
de-scramble strip geometry, Base64 tolerance, the AES round trip, MD5 vectors and full
string-table coverage:

```bash
gradlew.bat :desktop:test
```

## How it works (high level)

- **Host discovery** — on startup the app fetches an encrypted server list from a CDN,
  decrypts it, and picks a random API host.
- **Request signing** — every request carries `Tokenparam` / `Token` headers derived from
  the current time and a static key (matching the reference web client).
- **Response encryption** — API responses are AES-256-ECB encrypted; the app decrypts them
  before parsing.
- **Image de-scrambling** — the reader computes a deterministic slice count from
  `md5(albumId + pageName)` and re-assembles the reversed horizontal strips
  (`util/ImageDescrambler.kt`).

## License

[GNU General Public License v3.0](LICENSE) — see the `LICENSE` file.

Copyright © 2026 — contributions welcome.
