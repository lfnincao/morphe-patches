package app.morphe.patches.mxplayer.misc.fix

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.cloneParameters
import app.morphe.patches.all.misc.fix.spoofsignature.spoofSignaturePatch
import app.morphe.patches.mxplayer.misc.extension.sharedExtensionPatch
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_PRO

/**
 * MX Player Pro loads `libmx-bh.so` from [MX_BH_CLASS] / [MX_AS_CLASS] static initializers.
 * On arm64 with heap tagging (Android 14+), JNI init and background verifier threads inside
 * that library abort the process when the APK is re-signed.
 *
 * v2.4.3 only skipped BH.d/BH.b; the library still loaded and crashed via other paths
 * (e.g. tagged-pointer `free()` in verifier worker code).
 *
 * This patch never loads the library: `<clinit>` returns immediately, every JNI export is a
 * Java stub, and the `.so` is removed from the APK. Signature spoof remains for any Java-side
 * PackageManager checks (requires an unmodified stock APK as patch input).
 */
@Suppress("unused")
val mxPlayerPatchedInstallPatch = patch(
    name = "MX Player patched install support",
    description = "Keeps MX Player Pro launchable on Morphe re-signed builds by not loading " +
        "libmx-bh at all (stub com.ddx.axx.axx.axx BH/AS/AG natives and drop the library), " +
        "plus spoofing the original package signature when patching from a stock APK.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)

    dependsOn(sharedExtensionPatch, spoofSignaturePatch)

    execute {
        disableLibMxBhLoadingAndNative()

        // Emulator / BlueStacks gate in BH.c (non-native); avoid false positives when testing.
        MxBhEmulatorGateCFingerprint.method.cloneParameters().apply {
            addInstructions(0, """
                const/4 v0, 0x0
                return v0
            """.trimIndent())
        }
    }

    finalize {
        removeLibMxBhFromApk()
    }
}