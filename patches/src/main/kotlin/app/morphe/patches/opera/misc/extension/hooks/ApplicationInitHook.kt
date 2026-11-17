package app.morphe.patches.opera.misc.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patches.all.misc.extension.ExtensionHook

internal object OperaApplicationOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/opera/android/OperaApplication;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf(),
)

internal val operaApplicationOnCreateHook = ExtensionHook(OperaApplicationOnCreateFingerprint)