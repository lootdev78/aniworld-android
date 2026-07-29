# Keep model names used by HTML/JSON diagnostics and Room-generated code.
-keep class io.github.lootdev78.aniworld.**Entity { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn org.conscrypt.**
-dontwarn okhttp3.internal.platform.**

# Optional Samsung Smart View SDK is called through reflection. Keep its public API intact when
# the licensed vendor JAR/AAR is supplied in app/libs; these rules are harmless when it is absent.
-keep class com.samsung.multiscreen.** { *; }
-keep interface com.samsung.multiscreen.** { *; }
-dontwarn com.samsung.multiscreen.**
# Compatibility exceptions recommended for the Samsung Android sender package.
-dontwarn lombok.**
-dontwarn com.samsung.multiscreen.BuildConfig
-dontwarn javax.jmdns.impl.DNSCache

