package app.morphe.patches.opera.video.speed

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.Opcode

/**
 * Fired when tab load progress changes. A {@code false} argument means loading finished;
 * verified in Opera 103.2.5234.90977 {@code OperaWebContentsDelegate.loadingStateChanged}.
 *
 * Hook placement uses a structural fingerprint on Chromium load state (not obfuscated class
 * names), consistent with Vogelkop-MorphePatches style. Script eval mirrors stock
 * {@code WebContentsImpl.e + J.N.MevqfbP8}, not {@code WebContents.R0}/{@code MseJ7A4a}.
 * Vogelkop does not ship playback speed;
 * startup companions are operaHideBrokenInstallationDialogPatch and operaIgnoreBootFailuresPatch.
 */
internal val operaWebContentsLoadingStateChangedFingerprint = Fingerprint(
    definingClass = "Lcom/opera/android/browser/chromium/OperaWebContentsDelegate;",
    name = "loadingStateChanged",
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Lcom/opera/android/browser/chromium/ChromiumContent;",
        ),
    ),
)
