package app.morphe.patches.mxplayer.misc.fix

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

/** [com.ddx.axx.axx.axx.BH.c] emulator / clone-environment probe (non-native). */
internal object MxBhEmulatorGateCFingerprint : Fingerprint(
    definingClass = MX_BH_CLASS,
    name = "c",
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;"),
)

/** `const-string "mx-bh"` in obfuscated Application [LlV.onCreate]. */
internal object MxLvOnCreateMxBhStringFingerprint : Fingerprint(
    definingClass = "LlV;",
    name = "onCreate",
    returnType = "V",
    filters = listOf(string("mx-bh")),
)