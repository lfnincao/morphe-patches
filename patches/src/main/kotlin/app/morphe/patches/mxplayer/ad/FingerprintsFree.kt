package app.morphe.patches.mxplayer.ad

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/** Force local ad pool factory to return the built-in no-op [Lm39;]. */
internal object LocalAdFactoryFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ad/local/ad/a;",
    name = "f",
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Lm39;",
    filters = listOf(
        string("OTT"),
    ),
)

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