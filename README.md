# ZapFlick

Personal-use Android video downloader built on [yt-dlp](https://github.com/yt-dlp/yt-dlp) via
[youtubedl-android](https://github.com/JunkFood02/youtubedl-android). Sideload only, not for the Play Store.

Kotlin, Jetpack Compose, Material 3, Hilt, Room, a foreground service. Min SDK 26, target SDK 35.

## Status: Phase 2 (merged Phase 1 + 2)

Working now:

- Paste a link, or share one from any app (`ACTION_SEND` text/plain)
- Metadata fetch (title, thumbnail, duration, uploader)
- Quality sheet: best quality, each resolution the site offers, or audio only (MP3 / M4A), with
  estimated sizes (sizes are estimates, and some sites report none)
- Download queue stored in Room: oldest first, two at a time
- Pause, resume, cancel and retry per download. Pausing keeps the partial files, so resume continues
  where it stopped
- Downloads screen with live progress, plus history of finished, failed and cancelled items
- One foreground-service notification for the whole queue ("Pause all" / "Cancel all"), and a
  notification when each download finishes or fails
- Downloads interrupted by the app being killed are re-queued the next time the app opens
- Finished files are published to `Movies/ZapFlick` (video) or `Music/ZapFlick` (audio) through MediaStore

Not yet (later phases): settings (concurrency, theme, location), playlists, in-app yt-dlp update.

Phase 2 replaced WorkManager with an app-owned queue (`DownloadQueueManager`) and a foreground
service. WorkManager cannot pause a job or limit how many run at once.

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

Sites change often, and downloads break when yt-dlp falls behind.

- **Now:** bump `youtubedl` in `gradle/libs.versions.toml` to the newest release of
  `io.github.junkfood02.youtubedl-android`, then rebuild and reinstall.
- **Phase 3:** a Settings button will update yt-dlp in place (stable or nightly channel) and show the
  current version, with no reinstall.

## Storage

Files go through MediaStore, so the app needs no storage permission. On Android 8-9 (API 26-28),
MediaStore writes need a legacy permission, so downloads there are saved to the app's own folder
(`Android/data/com.najmulcodes.zapflick/files/Movies`) instead.

## How downloads work

`DownloadQueueManager` owns the queue. Room is the source of truth for every item's state
(`QUEUED`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`); the manager owns the coroutines
that move items between states, and runs at most two at once (`QueueConfig`). Live progress is kept
in memory only. `DownloadService` is a thin foreground service: it mirrors the queue into a
notification and stops itself when nothing is queued or running.

Each download works in its own folder under the app's private storage until it is saved. Partial
files survive a pause and a network failure, so retry resumes them. They are deleted on cancel,
on other failures, and after a successful save.

## Layout

```
app/src/main/java/com/najmulcodes/zapflick/
  domain/   models, use cases, the queue (DownloadQueueManager) and the interfaces it depends on
  data/     YoutubeDlEngine (the only file that touches the library), option, error and format
            parsing, Room database, MediaStore saver, PDF session (PdfRenderer)
  service/  DownloadService, notifications, the Android side of the queue
  ui/       theme, navigation, Home and Downloads screens, components, viewer (ViewerActivity)
  di/       Hilt bindings
```
