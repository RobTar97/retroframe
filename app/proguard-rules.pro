# RetroFrame ProGuard / R8 rules
#
# Release builds run with isMinifyEnabled = true and isShrinkResources = true.
# On the old, slow-storage devices this app targets, a smaller DEX measurably
# improves cold start, so shrinking is worth the extra care these rules require.

# Keep line numbers so release stack traces in bug reports are usable,
# but hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Glide
# ---------------------------------------------------------------------------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# ---------------------------------------------------------------------------
# AndroidX Media3 (ExoPlayer)
#
# Media3 loads renderers, decoders and extractors reflectively by class name.
# Without these keeps, video playback fails only in release builds — an
# expensive class of bug to discover after shipping.
# ---------------------------------------------------------------------------
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep interface androidx.media3.common.** { *; }

# ---------------------------------------------------------------------------
# PhotoView
# ---------------------------------------------------------------------------
-keep class com.github.chrisbanes.photoview.** { *; }

# ---------------------------------------------------------------------------
# App classes referenced from XML or the manifest rather than from code.
# R8 cannot see these references, so they must be kept explicitly.
# ---------------------------------------------------------------------------
-keep class com.rober.photoframe.PhotoframeApp
-keep class com.rober.photoframe.MainActivity
-keep class com.rober.photoframe.boot.BootReceiver
-keep class com.rober.photoframe.schedule.AlarmReceiver
-keep class com.rober.photoframe.schedule.AlarmDismissReceiver
-keep class com.rober.photoframe.schedule.DailySchedule$ScheduleReceiver

# Custom views inflated from layout XML need their two/three-arg constructors.
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ---------------------------------------------------------------------------
# Kotlin
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Strip verbose/debug logging from release builds. These calls run on every
# slideshow advance and every folder scan; removing them avoids the string
# concatenation work entirely rather than just discarding the result.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
