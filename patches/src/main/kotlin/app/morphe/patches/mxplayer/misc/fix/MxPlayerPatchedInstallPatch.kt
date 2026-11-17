package app.morphe.patches.mxplayer.misc.fix

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.cloneParameters
import app.morphe.patches.all.misc.fix.spoofsignature.spoofSignaturePatch
import app.morphe.patches.mxplayer.misc.extension.sharedExtensionPatch
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_PRO

/**
 * MX Player Pro loads `libmx-bh.so` on startup via obfuscated [com.ddx.axx.axx.axx.BH] and aborts
 * (~1s) when the install APK is not signed with the original publisher certificate.
 *
 * Hooks PackageManager through Morphe's signature-spoof extension and no-ops the Java stubs that
 * spawn the native verifier thread (BH.d / BH.b → BH.n).
 */
@Suppress("unused")
val mxPlayerPatchedInstallPatch = patch(
    name = "MX Player patched install support",
    description = "Restores launch on re-signed builds by spoofing the original package signature for " +
        "Java-side checks and skipping libmx-bh native install verification (BH.d / BH.b).",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)

    dependsOn(sharedExtensionPatch, spoofSignaturePatch)

    execute {
        MxBhStartupDFingerprint.method.cloneParameters().apply {
            addInstructions(
                0,
                """
                    return-void
                """.trimIndent(),
            )
        }

        MxBhStartupBFingerprint.method.cloneParameters().apply {
            addInstructions(
                0,
                """
                    return-void
                """.trimIndent(),
            )
        }

        // Native BH.w cannot be bytecode-patched; skip entire startup stubs instead.
        MxAppStartupFFingerprint.method.cloneParameters().apply {
            addInstructions(0, "return-void")
        }
        MxAppStartupGFingerprint.method.cloneParameters().apply {
            addInstructions(0, "return-void")
        }
    }
}