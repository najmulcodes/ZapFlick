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
