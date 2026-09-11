# ==============================================================================
# Shared ProGuard / R8 Rules for FolderShare (:shared)
# Applied to both Android (:androidApp) and Desktop (:desktopApp) targets
# ==============================================================================

# Preserve line numbers and reflection attributes for readable stack traces & reflection
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# Preserve native method names and classes containing native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ==============================================================================
# Compose Multiplatform & Skiko
# ==============================================================================
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }

# ==============================================================================
# Netty (used by Ktor Server) & Optional Codecs / Native Integrations
# ==============================================================================
-dontwarn java.lang.invoke.**
-dontwarn sun.misc.**
-dontwarn java.nio.channels.**
-dontwarn java.util.concurrent.**
-dontwarn javax.security.cert.**
-dontwarn javax.annotation.**

-dontwarn io.netty.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn io.netty.pkitesting.**
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.google.protobuf.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn com.oracle.svm.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.jboss.marshalling.**
-dontwarn org.osgi.**
-dontwarn reactor.blockhound.**
-dontwarn org.slf4j.**

-keep class io.netty.** { *; }
-keep interface io.netty.** { *; }

# Keep Netty native transport classes so transport availability checks don't fail unexpectedly
-keep class io.netty.channel.epoll.** { *; }
-keep class io.netty.channel.kqueue.** { *; }
-keep class io.netty.channel.unix.** { *; }

# Keep internal reflection used by Netty platform layer
-keep class io.netty.util.internal.shaded.org.jctools.** { *; }
-keep class io.netty.util.internal.PlatformDependent0 { *; }
-keep class io.netty.util.internal.PlatformDependent { *; }

# ==============================================================================
# Ktor Server & Client
# ==============================================================================
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-keep class io.ktor.server.** { *; }
-keep class io.ktor.server.netty.** { *; }
-keep class io.ktor.server.engine.** { *; }

# ==============================================================================
# JmDNS (mDNS Service Discovery)
# ==============================================================================
-dontwarn javax.jmdns.**
-dontwarn org.jmdns.**
-keep class javax.jmdns.** { *; }
-keep class org.jmdns.** { *; }

# ==============================================================================
# Okio & OkHttp
# ==============================================================================
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-keep class okio.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ==============================================================================
# Kotlinx Serialization & Coroutines
# ==============================================================================
-dontnote kotlinx.serialization.**
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class * implements kotlinx.serialization.KSerializer {
    *;
}
-keep @kotlinx.serialization.Serializable class * {
    *;
}

# ==============================================================================
# Room & SQLite
# ==============================================================================
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ==============================================================================
# Koin Dependency Injection
# ==============================================================================
-dontwarn org.koin.**
-dontwarn io.insert.koin.**
-keep class org.koin.** { *; }
-keep class io.insert.koin.** { *; }

# ==============================================================================
# Kermit / Ksoup / FileKit
# ==============================================================================
-dontwarn co.touchlab.kermit.**
-keep class co.touchlab.kermit.** { *; }
-keep class com.fleeksoft.ksoup.** { *; }
-keep class io.github.vinceglb.filekit.** { *; }

