# --- youtubedl-android (library + ffmpeg + aria2c) ---
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }
-keepclassmembers class com.yausername.youtubedl_android.mapper.** { *; }

# The library maps yt-dlp JSON output onto these classes with Jackson (reflection).
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* *;
}
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn com.fasterxml.jackson.databind.**

# Archive extraction used while unpacking the bundled python/yt-dlp.
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**

# Crash reports stay readable.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# --- Settings and enums stored by name ---
# DataStore keeps enum names (ThemeMode, SearchEngine, FilenameStyle, YtDlpChannel) as text, and
# the Wi-Fi gate and PIN code read them back with enumValues(), so keep them readable.
-keepclassmembers enum com.najmulcodes.zapflick.domain.settings.** { *; }

# --- Room ---
# Entities, DAOs and the migrations are referenced by Room's generated code; the Room and
# Hilt consumer rules cover the rest. Migrations must stay: removing one would wipe history on upgrade.
-keep class com.najmulcodes.zapflick.data.db.Migrations { *; }

# --- Media3 (private folder player), Biometric, DataStore, WebView ---
# These libraries ship their own consumer rules. ZapFlick adds no JavaScript interfaces to any
# WebView, so there is nothing to keep for them.
