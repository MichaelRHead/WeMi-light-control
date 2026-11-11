# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information so Play Console deobfuscates R8 stack traces cleanly.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve domain enums whose constant names are persisted in Room SQLite (e.g. DeviceType.valueOf).
-keep enum org.suppressingfire.wemi.** { *; }

# Preserve Room v5 database, DAO, generated *_Impl classes, and @Entity models.
-keep @androidx.room.Entity class org.suppressingfire.wemi.data.** { *; }
-keep @androidx.room.Database class org.suppressingfire.wemi.data.** { *; }
-keep @androidx.room.Dao class org.suppressingfire.wemi.data.** { *; }
-keep class org.suppressingfire.wemi.data.**_Impl { *; }

# LanRuntimeUtils.isRunningInRobolectric() probes org.robolectric.Robolectric via runCatching.
-dontwarn org.robolectric.**
