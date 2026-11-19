-dontobfuscate
-dontoptimize
-keepattributes *
-keep class app.morphe.** {
  *;
}
-keepclassmembers class app.morphe.extension.mxplayerpro.delete.DeleteShortcutPatch {
    public static <methods>;
}
-keepclassmembers class app.morphe.extension.mxplayerfree.delete.DeleteShortcutPatch {
    public static <methods>;
}
-keepclassmembers class app.morphe.extension.mxplayerfree.modsettings.ModSettings {
    public static <methods>;
}
-keepclassmembers class app.morphe.extension.mxplayerfree.modsettings.ModSettingsRow {
    public static <methods>;
}
-keepclassmembers class app.morphe.extension.mxplayerfree.modsettings.ModViewHider {
    public static <methods>;
}
-keep class com.google.** {
  *;
}
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn javax.lang.model.element.Modifier
