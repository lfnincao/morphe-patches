package app.morphe.patches.mxplayer.ad

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.cloneParameters
import app.morphe.patches.mxplayer.misc.fix.mxPlayerPatchedInstallPatch
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_FREE
import com.android.tools.smali.dexlib2.Opcode

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

        GlobalNativeAdsLoadFingerprint.method.cloneParameters().apply {
            addInstructions(0, "return-void")
        }

        BetweenTrayNativeAdBinderP32Fingerprint.method.cloneParameters().apply {
            addInstructions(0, "return-void")
        }

        BetweenTrayNativeAdBinderThiFingerprint.method.cloneParameters().apply {
            addInstructions(0, "return-void")
        }

        BannerViewShowFingerprint.method.cloneParameters().apply {
            addInstructions(0, "return-void")
        }

        BannerViewOnAttachedFingerprint.method.cloneParameters().apply {
            addInstructions(
                1,
                """
                const/16 v0, 0x8
                invoke-virtual {p0, v0}, Landroid/view/View;->setVisibility(I)V
                return-void
                """.trimIndent(),
            )
        }

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