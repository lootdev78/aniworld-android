# Keep model names used by HTML/JSON diagnostics and Room-generated code.
-keep class io.github.lootdev78.aniworld.**Entity { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn org.conscrypt.**
-dontwarn okhttp3.internal.platform.**
