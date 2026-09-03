-keepattributes *Annotation*
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-keep class com.noveltoon.app.data.entity.** { *; }
-keep class com.noveltoon.app.data.parser.** { *; }
-dontwarn org.jsoup.**
