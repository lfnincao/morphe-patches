package app.morphe.patches.mxplayer.ad

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.cloneParameters
import app.morphe.patches.mxplayer.ad.removeads.mxPlayerUniversalRemoveAdsPatch
import app.morphe.patches.mxplayer.misc.fix.mxPlayerPatchedInstallPatch
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_FREE
import com.android.tools.smali.dexlib2.Opcode

/**
 * MX Player free ad removal: FTL-Patches universal strip/hide (see [mxPlayerUniversalRemoveAdsPatch])
 * plus interstitial activity dismiss.
 */
@Suppress("unused")
val mxPlayerRemoveAdsPatch = patch(
    name = "MX Player remove ads",
    description = "FTL-style SDK/layout ad removal for com.mxtech.videoplayer.ad; dismisses full-screen interstitial.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_FREE)
    dependsOn(mxPlayerPatchedInstallPatch, mxPlayerUniversalRemoveAdsPatch)

    execute {
        MxInterstitialAdActivityOnCreateFingerprint.method.cloneParameters().apply {
            val superIndex = implementation!!.instructions.indexOfFirst { it.opcode == Opcode.INVOKE_SUPER }
            check(superIndex >= 0) { "invoke-super not found in MxInterstitialAdActivity.onCreate" }
            addInstructions(
                superIndex + 1,
                """
                invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                return-void
                """.trimIndent(),
            )
        }
    }
}