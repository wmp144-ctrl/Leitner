# WazheBox — R8 rules

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ir.wazhebox.**$$serializer { *; }
-keepclassmembers class ir.wazhebox.** {
    *** Companion;
}
-keepclasseswithmembers class ir.wazhebox.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Glance
-keep class androidx.glance.** { *; }
