-dontobfuscate
-dontoptimize
-keepattributes *
-keep class app.morphe.** {
  *;
}
-keepclassmembers class app.morphe.extension.mxplayerpro.delete.DeleteShortcutPatch {
    public static <methods>;
}
-keep class com.google.** {
  *;
}
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn javax.lang.model.element.Modifier
