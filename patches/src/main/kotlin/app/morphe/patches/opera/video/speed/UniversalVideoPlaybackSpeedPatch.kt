package app.morphe.patches.opera.video.speed

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.opera.misc.extension.hooks.OperaApplicationOnCreateFingerprint
import app.morphe.patches.opera.misc.extension.operaExtensionDexPatch
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA_DIRECT
import app.morphe.patches.opera.startup.operaHideBrokenInstallationDialogPatch
import app.morphe.patches.opera.startup.operaIgnoreBootFailuresPatch
import app.morphe.patches.shared.misc.bridge.addBridge
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.util.MethodUtil
import java.util.Locale

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/opera/patches/UniversalVideoPlaybackSpeedPatch;"
private const val SCRIPT_INJECTOR_CLASS =
    "Lapp/morphe/extension/opera/patches/OperaPlaybackSpeedScriptInjector;"
private const val OPERA_PROCESS_GUARD_CLASS = "Lapp/morphe/extension/opera/OperaProcessGuard;"
private const val SHARED_UTILS_CLASS = "Lapp/morphe/extension/shared/Utils;"

private const val WEB_CONTENTS_WRAPPER_CLASS = "Lcom/opera/android/browser/WebContentsWrapper;"
private const val OPERA_DELEGATE_CLASS = "Lcom/opera/android/browser/chromium/OperaWebContentsDelegate;"
private const val CHROMIUM_CONTENT_CLASS = "Lcom/opera/android/browser/chromium/ChromiumContent;"

private const val WEB_CONTENTS_CLASS = "Lorg/chromium/content_public/browser/WebContents;"

@Suppress("unused")
val operaUniversalVideoPlaybackSpeedPatch = patch(
    name = "Universal video playback speed",
    description = "Applies a configurable default playback speed to HTML5 video and audio on " +
        "every page by injecting a script when a tab finishes loading (stock " +
        "WebContentsImpl.e + b / J.N.MevqfbP8). Supports 0.25x through 3x (including 2.5x). Adds a " +
        "Default playback speed row under Opera Settings → Media (FTL-Patches-style layout hook; " +
        "SharedPreferences; patch option sets the initial default). Includes Vogelkop-MorphePatches " +
        "startup helpers for emulators. Target: Opera Browser 103.3.5234.91185. Not compatible with " +
        "the Clone app / package-rename patch.",
) {
    val playbackSpeedOption = stringOption(
        key = "playbackSpeed",
        default = "1",
        values = mapOf(
            "0.25x" to "0.25",
            "0.5x" to "0.5",
            "0.75x" to "0.75",
            "1x (normal)" to "1",
            "1.25x" to "1.25",
            "1.5x" to "1.5",
            "1.75x" to "1.75",
            "2x" to "2",
            "2.5x" to "2.5",
            "3x" to "3",
        ),
        title = "Default HTML5 playback speed",
    )

    compatibleWith(COMPATIBILITY_OPERA, COMPATIBILITY_OPERA_DIRECT)

    dependsOn(
        operaExtensionDexPatch,
        operaPlaybackSpeedSettingsResourcePatch,
        operaHideBrokenInstallationDialogPatch,
        operaIgnoreBootFailuresPatch,
    )

    execute {
        val speed = playbackSpeedOption.value!!.toFloat()
        check(speed in 0.25f..3f)

        val loadingMatch = operaWebContentsLoadingStateChangedFingerprint.match()
        val chromiumField = chromiumContentFieldFromLoadingStateChanged(loadingMatch.method)
        installOperaDelegateScriptBridge(chromiumField)
        patchExtensionPatchDefaultPlaybackSpeed(speed)

        loadingMatch.method.addInstruction(
            0,
            """
                invoke-static {p0, p1}, $EXTENSION_CLASS->onLoadingStateChanged(Ljava/lang/Object;Z)V
            """,
        )

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }

    finalize {
        installOperaProcessGuardOnApplicationOnCreate()
    }
}

private fun installOperaProcessGuardOnApplicationOnCreate() {
    OperaApplicationOnCreateFingerprint.clearMatch()
    val onCreate = OperaApplicationOnCreateFingerprint.method
    val mutableMethod = mutableClassDefBy(onCreate.definingClass).methods.first {
        MethodUtil.methodSignaturesMatch(it, onCreate)
    }
    val instructions = mutableMethod.implementation?.instructions
        ?: error("OperaApplication.onCreate has no implementation")
    var replaced = false
    instructions.forEachIndexed { index, instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: return@forEachIndexed
        if (reference.definingClass == SHARED_UTILS_CLASS && reference.name == "setContext") {
            mutableMethod.replaceInstruction(
                index,
                "invoke-static/range { p0 .. p0 }, $OPERA_PROCESS_GUARD_CLASS->initMainProcessContext(Landroid/content/Context;)V",
            )
            replaced = true
        }
    }
    if (!replaced) {
        mutableMethod.addInstruction(
            0,
            """
                invoke-static/range { p0 .. p0 }, $OPERA_PROCESS_GUARD_CLASS->initMainProcessContext(Landroid/content/Context;)V
            """,
        )
    }
}

private fun installOperaDelegateScriptBridge(chromiumField: String) {
    val delegate = mutableClassDefBy(OPERA_DELEGATE_CLASS)
    val method = ImmutableMethod(
        OPERA_DELEGATE_CLASS,
        "patch_executePlaybackSpeedScript",
        listOf(
            ImmutableMethodParameter(OPERA_DELEGATE_CLASS, null, "delegate"),
            ImmutableMethodParameter("Ljava/lang/String;", null, "script"),
        ),
        "V",
        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
        null,
        null,
        MutableMethodImplementation(4),
    ).toMutable()

    method.addInstructions(
        0,
        """
            if-eqz p0, :return
            if-eqz p1, :return
            iget-object v0, p0, $OPERA_DELEGATE_CLASS->$chromiumField:$CHROMIUM_CONTENT_CLASS
            if-eqz v0, :return
            invoke-virtual {v0}, $WEB_CONTENTS_WRAPPER_CLASS->e()$WEB_CONTENTS_CLASS
            move-result-object v0
            if-eqz v0, :return
            invoke-static {v0, p1}, $SCRIPT_INJECTOR_CLASS->evaluate(${WEB_CONTENTS_CLASS}Ljava/lang/String;)V
            :return
            return-void
        """,
    )

    delegate.methods.add(method)
}

private fun chromiumContentFieldFromLoadingStateChanged(
    method: com.android.tools.smali.dexlib2.iface.Method,
): String {
    val impl = method.implementation
        ?: error("OperaWebContentsDelegate.loadingStateChanged has no implementation")
    for (inst in impl.instructions) {
        if (inst.opcode != Opcode.IGET_OBJECT) continue
        val ref = inst as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
            ?: continue
        val field = ref.reference as? com.android.tools.smali.dexlib2.iface.reference.FieldReference
            ?: continue
        if (field.type == CHROMIUM_CONTENT_CLASS) {
            return field.name
        }
    }
    error("Could not resolve ChromiumContent field on OperaWebContentsDelegate")
}

private fun patchExtensionPatchDefaultPlaybackSpeed(speed: Float) {
    val smaliConst = floatLiteralSmali(speed)

    mutableClassDefBy(EXTENSION_CLASS).apply {
        methods.removeAll { it.name == "getPatchDefaultPlaybackSpeed" }
        addBridge(
            name = "getPatchDefaultPlaybackSpeed",
            parameters = emptyList(),
            returnType = "F",
            registerCount = 1,
            smali = """
                $smaliConst
                return v0
            """,
            accessFlags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
        )
    }
}

/** Smali that loads [speed] into v0 for a float return. */
private fun floatLiteralSmali(speed: Float, register: String = "v0"): String {
    val bits = speed.toBits()
    return if (bits and 0xffff == 0) {
        val literal = speed.toString().let { if (it.contains('.')) it else "$it.0" }
        "const/high16 $register, ${literal}f"
    } else {
        "const $register, 0x${String.format(Locale.US, "%08x", bits)}"
    }
}