package app.morphe.patches.mxplayer.ad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/** Skip interstitial activity UI ([com.mxplay.monetize.MxInterstitialAdActivity]). */
internal object MxInterstitialAdActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/mxplay/monetize/MxInterstitialAdActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        opcode(Opcode.INVOKE_SUPER),
        fieldAccess(
            name = "activity_native_full_screen_ad",
            type = "I",
            opcode = Opcode.SGET,
        ),
        opcode(Opcode.INVOKE_VIRTUAL),
    ),
)