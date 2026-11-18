/*
 * Adapted from Vogelkop-MorphePatches (https://github.com/maniesh-NR/Vogelkop-MorphePatches)
 * HideBrokenInstallationDialogPatch.kt — MIT-compatible Morphe patch structure.
 */

package app.morphe.patches.opera.startup

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.patch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA_DIRECT
import com.android.tools.smali.dexlib2.Opcode

private object BrokenInstallDialogFingerprint : Fingerprint(
    isStatic = true,
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;", "I"),
    filters = listOf(
        resourceLiteral(ResourceType.STRING, "welcome_init_emulated_arm"),
        resourceLiteral(ResourceType.STRING, "welcome_init_failure"),
        string("com.android.vending"),
    ),
)

private object InstallationBrokenFlagFingerprint : Fingerprint(
    definingClass = "Lcom/opera/android/OperaApplication;",
    returnType = "Z",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/Integer;",
        ),
    ),
)

@Suppress("unused")
val operaHideBrokenInstallationDialogPatch = patch(
    name = "Hide broken installation dialog",
    description = "Prevents Opera's \"Your app installation appears to be broken\" dialog from " +
        "blocking startup. Fingerprints from Vogelkop-MorphePatches (crash-recovery nag, not a " +
        "signature check). Recommended alongside playback-speed patches on emulators and patched installs.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_OPERA, COMPATIBILITY_OPERA_DIRECT)

    execute {
        BrokenInstallDialogFingerprint.method.returnEarly()
        InstallationBrokenFlagFingerprint.method.returnEarly(false)
        println("Opera: hid the broken-installation dialog (2 methods patched).")
    }
}