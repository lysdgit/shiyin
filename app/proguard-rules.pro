# kotlinx-serialization 自带 consumer rules,这里补一层保险
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.shiyin.player.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.shiyin.player.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.shiyin.player.**$$serializer { *; }
