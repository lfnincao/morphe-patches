package app.morphe.patches.opera.video.speed

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.Opcode

/**
 * Fired when tab load progress changes. A {@code false} argument means loading finished;
 * verified in Opera 103.2.5234.90977 {@code OperaWebContentsDelegate.loadingStateChanged}.
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

/** Inflates Opera Settings → Media ({@code fragment_media_settings}). */
internal val operaMediaSettingsInflateFingerprint = Fingerprint(
    definingClass = "Ldid;",
    name = "h",
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
    ),
)