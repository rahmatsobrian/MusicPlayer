# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep source file names and line numbers for crash reports
-keepattributes SourceFile,LineNumberTable

# Keep model classes
-keep class com.musicplayer.app.model.** { *; }

# Keep service
-keep class com.musicplayer.app.service.MusicService { *; }
