# Project ProGuard / R8 Rules

# Preserve line numbers and reflection attributes for readable stack traces & reflection
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# Preserve native method names and classes containing native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ==============================================================================
# Netty (used by Ktor Server)
# ==============================================================================
-dontwarn io.netty.**
-dontwarn sun.misc.**
-dontwarn java.lang.invoke.**
-dontwarn java.nio.channels.**
-dontwarn java.util.concurrent.**
-dontwarn javax.security.cert.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn reactor.blockhound.**

# Keep all Netty classes, interfaces, and members to support Netty's dynamic reflection
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
-keep class javax.jmdns.** { *; }

# ==============================================================================
# Okio & OkHttp
# ==============================================================================
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ==============================================================================
# Kotlinx Serialization
# ==============================================================================
-dontnote kotlinx.serialization.**
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
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ==============================================================================
# Koin
# ==============================================================================
-dontwarn org.koin.**
-keep class org.koin.** { *; }

# ==============================================================================
# Kermit / Logging / Ksoup
# ==============================================================================
-dontwarn co.touchlab.kermit.**
-keep class com.fleeksoft.ksoup.** { *; }