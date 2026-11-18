package app.morphe.patches.mxplayer.ad

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.cloneParameters
import app.morphe.patches.mxplayer.misc.fix.mxPlayerPatchedInstallPatch
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_FREE

/**
 * Stubs free MX Player 3.5.3 ad entry points for local playback and interstitial activities.
 */
@Suppress("unused")
val mxPlayerRemoveAdsPatch = bytecodePatch(
    name = "MX Player remove ads",
    description = "Routes local ad factory to no-op and dismisses full-screen interstitial activity.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_FREE)
    dependsOn(mxPlayerPatchedInstallPatch)

    execute {
        LocalAdFactoryFingerprint.method.cloneParameters().apply {
            addInstructions(
                0,
                """
                new-instance v0, Lcom/mxtech/videoplayer/ad/local/ad/a${'$'}a;
                invoke-direct {v0}, Lcom/mxtech/videoplayer/ad/local/ad/a${'$'}a;-><init>()V
                return-object v0
                """.trimIndent(),
            )
        }

        MxInterstitialAdActivityOnCreateFingerprint.method.cloneParameters().apply {
            addInstructions(
                1,
                """
                invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                return-void
                """.trimIndent(),
            )
        }
    }
}