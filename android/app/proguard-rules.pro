# kotlinx-serialization: keep generated serializers and the classes they reflectively construct.
# https://github.com/Kotlin/kotlinx.serialization/blob/master/rules/common.pro
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.handspell.app.**$$serializer { *; }
-keepclassmembers class dev.handspell.app.** {
    *** Companion;
}
-keepclasseswithmembers class dev.handspell.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
