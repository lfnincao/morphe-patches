package app.morphe.patches.mxplayer.misc.fix

import app.morphe.patcher.Fingerprint

/** [com.ddx.axx.axx.axx.BH.c] emulator / clone-environment probe (non-native). */
internal object MxBhEmulatorGateCFingerprint : Fingerprint(
    definingClass = MX_BH_CLASS,
    name = "c",
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;"),
)