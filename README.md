# ZapFlick

Personal-use Android video downloader built on [yt-dlp](https://github.com/yt-dlp/yt-dlp) via
[youtubedl-android](https://github.com/JunkFood02/youtubedl-android). Sideload only, not for the Play Store.

Kotlin, Jetpack Compose, Material 3, Hilt, Room, a foreground service. Min SDK 26, target SDK 35.

## Status: version 0.3.0

The app has three tabs at the bottom (**Tab, Progress, Finished**), a Settings screen, a browser with
tabs, a PDF and HTML viewer, and a private folder behind a PIN.

- **Tab**: a new-tab page (search field, favorite sites you can add, remove and reorder, recently used
  websites) and a real browser with up to 20 tabs, ad blocking, desktop-site mode, fullscreen video and
  a Download button. Paste or type a link, or share one from another app (`ACTION_SEND` text/plain):
  a shared link opens in a new tab and the download sheet opens by itself.
- **Progress**: running, waiting, paused, failed and cancelled downloads with pause, resume, cancel and
  retry. A banner leads to *Background setup* until the battery exemption is granted.
- **Finished**: finished downloads as a list or grid. Long-press to select, then delete or move to the
  private folder. PDF and HTML files open in ZapFlick's viewer. A footer shows storage used.
- **Settings**: download folder, Wi-Fi only, how many downloads at once, default quality, file name
  style, ad blocking, search engine, clear cache, history and cookies, language, sync to gallery, theme,
  dynamic color, privacy switches, yt-dlp version and in-place update, help.
- **Private folder**: files moved here leave the gallery and the Finished list. See the limits below.

Downloads still work as before: an app-owned queue in Room, pause and resume that keep partial files,
one foreground-service notification, and re-queueing after the app is killed.

### Settings

Settings live in a DataStore behind the `SettingsRepository` interface (`domain/settings`). The queue
reads the concurrency limit and the Wi-Fi gate every time it decides what to start, so changes apply at
once.

| Setting | What it does |
|---|---|
| Download location | Default: `Movies/ZapFlick` and `Music/ZapFlick` through MediaStore. Or a folder picked with the system folder picker (`OpenDocumentTree`, permission persisted), saved through `TreeUriSaver`. No storage permission either way. |
| Sync to gallery | **On:** finished files go through MediaStore and show in the gallery. **Off:** they stay in `filesDir/library`, hidden from other apps, and are deleted when ZapFlick is uninstalled. They play in the in-app player. A chosen folder takes priority over this switch. |
| Wi-Fi only | A default-network callback (`NetworkMonitor`) decides between Wi-Fi/unmetered, mobile data and offline. On mobile data running downloads are paused and waiting ones are not started; when Wi-Fi returns, exactly the downloads the app paused are resumed (ones you paused yourself stay paused). |
| Downloads at once | 1 to 4. |
| Default quality | Ask every time, or a fixed quality that skips the sheet. |
| File name | One of four styles (title and id, title, uploader and title, date and title), turned into a yt-dlp template. A fixed list on purpose: free text could produce a broken template. |
| Language | Opens the system per-app language page (Android 13+). Only English ships, so `locales_config.xml` lists only `en`; add `values-bn/strings.xml` and a `bn` entry to offer Bangla. |
| Theme, dynamic color | System, dark or light (default dark). Dynamic color is off by default so the brand colors win. |
| yt-dlp | Shows the installed version, and *Update yt-dlp* downloads the stable or nightly build in place. |

### Browser

- Tabs: `TabManager` keeps the list (`TabList`, at most 20, always at least one). Only the tab on screen
  has a live `WebView`; leaving a tab saves its page state in memory and coming back restores it. The
  list (where each tab was, not its page history) is written to DataStore, so tabs survive the app being
  killed.
- Favorite sites and history are Room tables (`favorite_sites`, `browser_history`). The default sites
  are added once; deleting them all does not bring them back. History keeps one row per address, at most
  500, and records nothing while *Recently used websites* is off.
- **Ad blocking** matches a request's host (and its parent domains) against `assets/adblock_hosts.txt`
  in `shouldInterceptRequest` and answers with an empty response. The list is read from the app and
  **never downloaded at runtime**. Source: [StevenBlack/hosts](https://github.com/StevenBlack/hosts),
  unified hosts file, **MIT licence**, 72,233 domains, fetched 2026-10-05 and reduced to one host per
  line. To refresh it, replace the file with a newer copy in the same format. Hosts lists block whole
  domains, so some pages may look different; switch *Block ads* off in Settings if one breaks.
- Pop-ups: opened only after a tap, and then in the same tab. Camera, microphone and location are never
  granted to a page. Bad certificates are refused. Links to other apps (`intent:`, `market:`) are ignored.
- Load errors show a page with a Retry button. Fullscreen video uses an overlay with the system bars
  hidden and landscape allowed; Back leaves it.
- The browser still shares cookies, user agent and referer with yt-dlp, except for YouTube.

### Private folder

- The lock icon on the Finished screen opens it. The first time you set a 4-digit PIN (enter, then
  confirm); afterwards you enter it. Fingerprint or face unlock is an optional switch in Settings.
- The PIN is stored as **PBKDF2WithHmacSHA256, random 16-byte salt, 120,000 iterations**, compared in
  constant time. Wrong tries are counted: two are free, then the app locks for 30 seconds, 1 minute,
  5 minutes and 15 minutes, and the counter is stored, so restarting the app does not reset it.
- Moving a file copies it into `filesDir/private/<random>.<ext>`, checks the copy, then removes the public
  copy (and its MediaStore row, so it leaves the gallery). *Restore* puts it back through the normal
  saver. Private items never appear in Finished or Progress and are not touched by "Clear".
- Private videos and audio open in an in-app player, so the file is never handed to another app.
- The PIN and private screens block screenshots and the recents preview (`FLAG_SECURE`; a switch in
  Settings turns it off). The folder locks by itself after the app has been in the background for more
  than 30 seconds.
- **The PIN is a privacy gate, not encryption.** The files are ordinary files in the app's private
  storage: anyone who can read that storage (a rooted phone, a backup tool, a forensic tool) can read them.
  Encryption at rest (for example Tink streaming AEAD with a Keystore key) is deliberately **not** part of
  this version; it is planned as a separate step.

### Database

Room is at **version 2**. The migration from 1 (`MigrationSql.V1_TO_V2`) adds `downloads.is_private`
(default 0, so every existing download stays public) and the two browser tables. There is no destructive
fallback: a schema change must ship with a migration, or the history would be wiped. The SQL is checked
by `MigrationSqlTest` (it must never drop or delete anything and must use the names Room expects).

## PDF and HTML viewer

ZapFlick can open files handed to it by other apps. Tap a `.pdf`, `.html`, `.htm` or `.xhtml` file in
Files, Downloads, WhatsApp, Gmail or Chrome and ZapFlick appears in the "Open with" chooser. The file
opens in `ViewerActivity`, a separate Activity from `MainActivity` (launch mode `standard`, so Back
returns to the app the file came from). Nothing is copied to shared storage and no storage permission
is used.

The Activity answers four kinds of Intent: `ACTION_VIEW` with a PDF type, `ACTION_VIEW` with an HTML
type, `ACTION_VIEW` for file managers that send no type or `*/*` (matched by file name, including names
with extra dots and upper-case extensions), and `ACTION_SEND` of a PDF or HTML file (read from
`EXTRA_STREAM`). `DocumentTypeResolver` trusts a specific mime type and falls back to the extension.

How the PDF viewer works:

- `PdfSessionFactory` opens the file with `ContentResolver.openFileDescriptor`. `PdfRenderer` needs a
  seekable descriptor; if the provider hands over a pipe, the file is copied into `cacheDir` and the
  copy is deleted when the viewer closes (stale copies older than a day are swept on the next open).
- `PdfSession` renders one page at a time behind a `Mutex` on `Dispatchers.IO` (`PdfRenderer` allows a
  single open page) and always closes the page in `finally`. Rendered bitmaps live in an `LruCache`
  sized from a fraction of the heap, and bitmap size is capped (4096 px per edge, 8 million pixels) so a
  huge page or a high zoom cannot cause an out-of-memory crash.
- Zoom (pinch, or double tap) makes the pages wider and re-renders them at the new width. The list
  state and zoom are saved, so rotating the phone keeps your place.
- A password-protected or damaged PDF shows a message and an "Open in another app" button.

How the HTML viewer works:

- **Every HTML file is treated as hostile.** The `WebView` has JavaScript off, network loads blocked,
  and file, content and file-URL access off. DOM storage, geolocation and pop-up windows are off too,
  and mixed content is never allowed.
- Two switches in the toolbar menu, **Enable scripts** and **Allow online content**, are off for every
  file and are *not* remembered: they live in the ViewModel only, never in storage. The page reloads
  when one changes. A warning bar stays visible while either is on.
- The file is read as text (10 MB cap, enforced while reading, not only from the reported size) and
  given to the `WebView` with `loadDataWithBaseURL(null, ...)`. Because there is no base address,
  **images and style sheets stored next to the file do not load**; the screen says so. Inline
  (`data:`) images and styles inside the file work.
- `CharsetDetector` picks the encoding: byte order mark first, then a `<meta charset>` or
  `http-equiv` tag in the first 4 KB, then UTF-8. As in browsers, "iso-8859-1" and similar mean
  Windows-1252, and a UTF-16 label in a readable meta tag means UTF-8.
- Links: `http` and `https` open in the phone's browser, jumps within the page (`#section`) stay, every
  other scheme (`javascript:`, `file:`, `content:`, `intent:`, `mailto:`, `data:` and so on) is blocked
  (`HtmlLinkPolicy`).
- **View source** shows the file as selectable monospace text (first 100,000 characters of a large file).
- Back leaves the source view first, then goes back in the page, then closes the viewer. The `WebView`
  is paused with the screen, and stopped and destroyed when the viewer closes. If the page's renderer
  process is killed, the viewer shows a message instead of crashing the app.

Both viewers:

- The opened `Uri` is kept in `SavedStateHandle`. The read permission that came with the Intent does
  not survive process death, so when the system kills the app and later restores the screen, the
  viewer shows "Open the file again" instead of crashing.
- A `file:` link that points into ZapFlick's own private storage is refused, so another app cannot use
  the viewer to read ZapFlick's files.
- Android 10+ hides most of shared storage from a plain `file:` link. Apps that still send one cannot
  be read without a storage permission; ZapFlick says so instead of asking for the permission.

Unit tests for the pure logic: `DocumentTypeResolverTest`, `PdfMathTest`, `CharsetDetectorTest`,
`HtmlLinkPolicyTest`, `HtmlViewOptionsTest`, `SourcePreviewerTest`.

### Test checklist

`samples/` has `sample.pdf` (3 pages), `200-pages.pdf` (mixed page sizes), `locked.pdf` (password
`zapflick`, which ZapFlick does not support, so it must show the error screen), `sample.html` (script,
online image, relative image, links), `latin1.html` (Windows-1252 with a meta tag) and
`utf8-bom-bangla.html` (UTF-8 with a byte order mark and no meta tag).

First check that Android knows about the viewer. Both commands must list `ZapFlick`:

```
adb shell cmd package query-activities --brief -a android.intent.action.VIEW -t application/pdf
adb shell cmd package query-activities --brief -a android.intent.action.VIEW -t text/html
```

PDF:

- [ ] `adb push samples/sample.pdf /sdcard/Download/`, open it from the Files app, confirm ZapFlick
      appears in the chooser, choose "Always", reboot, open it again and confirm it still opens in ZapFlick
- [ ] Open a PDF whose name has extra dots (`adb push samples/sample.pdf /sdcard/Download/my.report.v2.pdf`)
- [ ] `200-pages.pdf` scrolls end to end without an out-of-memory crash, and the "Page n / 200" label follows
- [ ] Rotate while on page 50: the same page stays on screen
- [ ] Pinch and double tap zoom in and out; zoomed pages get sharper after you lift your fingers
- [ ] Open `locked.pdf`: a clear message and an "Open in another app" button, no crash
- [ ] Share and "Open with another app" (overflow menu) work, and the second chooser does not list ZapFlick
- [ ] Open a PDF from Gmail and from a WhatsApp attachment
- [ ] Open a PDF, press Home, kill ZapFlick from Recents or `adb shell am kill com.najmulcodes.zapflick.debug`,
      return to the viewer: either it reloads or it shows "Open the file again", never a crash

HTML:

- [ ] `adb push samples/sample.html /sdcard/Download/`, open it from Files, confirm ZapFlick is in the
      chooser (also try `my.page.v2.html` and `PAGE.HTM`), choose "Always", reboot, confirm it still opens
- [ ] `sample.html`: "Scripts: OFF", the `noscript` box is visible, the online and relative images are
      missing, the inline blue square shows
- [ ] Menu, **Enable scripts**: the page reloads, the text says "ON", the `noscript` box disappears,
      a red warning bar appears. Switch it off again and it goes back
- [ ] Menu, **Allow online content**: the online image appears (the relative one still does not)
- [ ] Open another file and then this one again: both switches are off again
- [ ] Links: the `https` link opens the browser; "Jump to the bottom" scrolls and Back returns to the top;
      the `mailto:`, `javascript:` and `file:` links do nothing
- [ ] `latin1.html` shows "Café" and curly quotes; `utf8-bom-bangla.html` shows Bangla with no stray characters
- [ ] **View source** shows the markup, text can be selected and copied; Back closes it and the page is as left
- [ ] Rotate with scripts on: the switches stay on and the page stays
- [ ] Too large: `head -c 12000000 /dev/zero | tr '\0' 'a' > big.html`, push it, open it: a "larger than 10 MB"
      message and an "Open in another app" button
- [ ] Open an HTML attachment from Gmail and from WhatsApp

## Phone checklist for 0.3.0

Run these on the phone after installing the CI build (the first install with a new signing key needs one
`adb uninstall com.najmulcodes.zapflick.debug`; after that `adb install -r` works).

1. [ ] Open a `.pdf` and a `.html` from the Files app: ZapFlick is in "Open with" and both render. The
       HTML page's script does nothing until *Enable scripts* is on.
2. [ ] The bottom bar shows **Tab, Progress, Finished**. Open the tabs drawer, open and close 3 tabs, then
       kill the app (`adb shell am force-stop com.najmulcodes.zapflick.debug`) and reopen it: the tabs are back.
3. [ ] Add a favorite site, remove one, move one earlier or later (long-press a tile). *Recently used websites*
       shows pages you visited only while that setting is on.
4. [ ] On a page full of ads, *Block ads* reduces ads and pop-ups and the shield counter goes up.
       Check that YouTube, Vimeo and TikTok still load and that the Download button still finds videos.
5. [ ] Play a video fullscreen (tap the fullscreen button), rotate, press Back: it leaves fullscreen cleanly and the
       bottom bar returns.
6. [ ] Start a download, turn the screen off for 10 minutes: it keeps going. The Progress banner
       "Improve download stability" disappears once the battery exemption is granted.
7. [ ] Turn on **Wi-Fi only**, switch Wi-Fi off (mobile data on): running downloads pause and Progress says it is
       waiting for Wi-Fi. Switch Wi-Fi on: they resume. A download you paused yourself stays paused.
8. [ ] Finished: long-press, select three items, delete: the files are gone from the Files app. The storage
       bar at the bottom matches *Settings, Storage* on the phone.
9. [ ] Select a file and tap the lock icon: first time you are asked to set a PIN. Move a file: it is gone from
       the gallery and from Finished. Open the private folder (PIN required), play it, restore it, delete another.
       Enter a wrong PIN three times: a 30-second lockout, and force-stopping the app does not reset it.
       Leave the app 40 seconds and come back: the PIN is asked again. The recents preview of the PIN screen is blank.
10. [ ] **Settings, yt-dlp, Update yt-dlp** shows the new version, and a download still works afterwards.
11. [ ] Settings: change the download location to a folder, download something, confirm it is in that folder;
        turn **Sync to gallery** off, download again: the file is not in the gallery and plays in the app.
12. [ ] *Default quality* set to a fixed value skips the quality sheet. *Downloads at once* of 1 starts one at a time.
13. [ ] Rotate each screen (Tab, Progress, Finished, Settings, PIN) and check dark and light themes.

## Requirements

- JDK 17 and the Android SDK (platform 35, build-tools 35.0.0). Android Studio is optional
- A device or emulator on Android 8.0+ (a real device is better; yt-dlp is heavy on emulators)

## Build and run (command line, Linux)

Point Gradle at your SDK once:

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

The Gradle wrapper (8.11.1) is included. Then:

```bash
./gradlew testDebugUnitTest     # unit tests
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In Android Studio, open the `ZapFlick` folder and let Gradle sync instead.
On Windows, use `gradlew.bat` in place of `./gradlew`.

The first launch unpacks the bundled Python and yt-dlp, which takes a few seconds. Fetching a link
before that finishes simply waits for it.

## Building without your own machine (GitHub Actions)

`.github/workflows/build.yml` runs the unit tests and builds the debug APK on every push to `main`
(and on demand from the Actions tab). Download `zapflick-debug-apk` from the run's Artifacts and
install it with `adb install -r app-debug.apk`. Use this on low-power or thermally limited laptops.

## Signing a release build

Create a keystore once (keep it and its passwords safe; you need the same key to update the app later):

```bash
keytool -genkeypair -v -keystore zapflick.jks -keyalg RSA -keysize 4096 -validity 10000 -alias zapflick
```

Create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=zapflick.jks
storePassword=your-store-password
keyAlias=zapflick
keyPassword=your-key-password
```

Build and install:

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties` the release build is produced unsigned and will not install.
Release builds use R8; the keep rules for youtubedl-android and its Jackson mapping are in
`app/proguard-rules.pro`. If a release build fails on a link that works in debug, suspect a missing
keep rule first.

## Updating yt-dlp

Sites change often, and downloads break when yt-dlp falls behind. **Settings, yt-dlp, Update yt-dlp**
updates it in place (stable or nightly channel) and shows the new version; no reinstall is needed. To
change the bundled version, bump `youtubedl` in `gradle/libs.versions.toml`.

## Storage

By default files go through MediaStore, so the app needs no storage permission (see Settings for the
chosen-folder and app-storage options). On Android 8-9 (API 26-28),
MediaStore writes need a legacy permission, so downloads there are saved to the app's own folder
(`Android/data/com.najmulcodes.zapflick/files/Movies`) instead.

## How downloads work

`DownloadQueueManager` owns the queue. Room is the source of truth for every item's state
(`QUEUED`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`); the manager owns the coroutines
that move items between states, and runs as many at once as Settings allows (`QueueConfig`, 1 to 4, default 2). Live progress is kept
in memory only. `DownloadService` is a thin foreground service: it mirrors the queue into a
notification and stops itself when nothing is queued or running.

Each download works in its own folder under the app's private storage until it is saved. Partial
files survive a pause and a network failure, so retry resumes them. They are deleted on cancel,
on other failures, and after a successful save.

## Layout

```
app/src/main/java/com/najmulcodes/zapflick/
  domain/   models, use cases, the queue (DownloadQueueManager) and the interfaces it depends on;
            pure logic for settings, tabs, ad-block matching, selection, PIN and the viewers
  data/     YoutubeDlEngine (the only file that touches the library, including the yt-dlp updater),
            Room database and migrations, savers (MediaStore, chosen folder, app storage), DataStore
            settings and PIN store, network monitor, ad blocker, PDF session (PdfRenderer)
  service/  DownloadService, notifications, the Android side of the queue
  ui/       theme, navigation (Tab, Progress, Finished), browser, settings, progress, finished, security
            (PIN and private folder), player, background setup, viewer (ViewerActivity)
  di/       Hilt bindings
```

## Tests and CI

`./gradlew testDebugUnitTest` runs the unit tests: pure logic only (settings, Wi-Fi policy, tabs, host
blocklist, PIN hashing and lockout, selection, storage, charset detection, link policy, the queue and
its Wi-Fi gate, library use cases, migration SQL). The GitHub Actions workflow builds the debug APK
first and uploads it, then runs the tests, so a failing test never blocks installing a build.

`app/debug.keystore` is committed (a debug key is not a secret) and used for debug builds, so every CI
APK has the same signature and `adb install -r` can update the previous one.

> Developer and AI handoff: see [docs/HANDOFF.md](docs/HANDOFF.md).
