# ==============================================================================
# Desktop-Specific ProGuard Rules (:desktopApp)
# Shared library rules are in :shared/proguard-rules.pro
# ==============================================================================

# Desktop main entry point
-keep class com.hz_apps.foldershare.MainKt {
    public static void main(java.lang.String[]);
}

# JVM Native Access / DBus / Desktop System Integration
-keep class com.sun.jna.** { *; }
-keep class org.freedesktop.dbus.** { *; }
-keep class org.slf4j.** { *; }
-keep class sun.misc.Unsafe { *; }
-dontwarn sun.misc.Unsafe

