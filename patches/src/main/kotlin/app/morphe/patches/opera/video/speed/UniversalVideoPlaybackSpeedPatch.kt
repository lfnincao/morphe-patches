package app.morphe.patches.opera.video.speed

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.opera.misc.extension.operaSharedExtensionPatch
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA_DIRECT
import app.morphe.patches.shared.misc.bridge.addBridge
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/opera/patches/UniversalVideoPlaybackSpeedPatch;"

private const val WEB_CONTENTS_WRAPPER_CLASS = "Lcom/opera/android/browser/WebContentsWrapper;"
private const val OPERA_DELEGATE_CLASS = "Lcom/opera/android/browser/chromium/OperaWebContentsDelegate;"
private const val CHROMIUM_CONTENT_CLASS = "Lcom/opera/android/browser/chromium/ChromiumContent;"

private const val WEB_CONTENTS_CLASS = "Lorg/chromium/content_public/browser/WebContents;"
private const val RENDER_FRAME_HOST_CLASS = "Lorg/chromium/content_public/browser/RenderFrameHost;"

@Suppress("unused")
val operaUniversalVideoPlaybackSpeedPatch = patch(
    name = "Universal video playback speed",
    description = "Applies a configurable default playback speed to HTML5 video and audio on " +
        "every page by injecting a script when a tab finishes loading. Supports 0.25x through " +
        "3x (including 2.5x). Target: Opera Browser 103.3.5234.91185 (and close Play/generic " +
        "builds). Does not change Opera's native download player or Chromium overlay menus; " +
        "those keep their built-in speed lists unless the page uses HTML5 media.",
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

    dependsOn(operaSharedExtensionPatch)

    execute {
        val speed = playbackSpeedOption.value!!.toFloat()
        check(speed in 0.25f..3f)

        installWebContentsWrapperEvaluateHelper()
        val loadingMatch = operaWebContentsLoadingStateChangedFingerprint.match()
        val chromiumField = chromiumContentFieldFromLoadingStateChanged(loadingMatch.method)
        installOperaDelegateScriptBridge(chromiumField)
        patchExtensionPlaybackSpeed(speed)

        loadingMatch.method.addInstruction(
            0,
            """
                invoke-static {p0, p1}, $EXTENSION_CLASS->onLoadingStateChanged(Ljava/lang/Object;Z)V
            """,
        )

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}

private fun installWebContentsWrapperEvaluateHelper() {
    val wrapper = mutableClassDefBy(WEB_CONTENTS_WRAPPER_CLASS)
    val method = ImmutableMethod(
        WEB_CONTENTS_WRAPPER_CLASS,
        "patch_evaluateJavascript",
        listOf(
            ImmutableMethodParameter(WEB_CONTENTS_CLASS, null, "webContents"),
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
            invoke-interface {p0}, $WEB_CONTENTS_CLASS->F()$RENDER_FRAME_HOST_CLASS
            move-result-object v0
            if-eqz v0, :return
            const/4 v1, 0x0
            const/4 v2, 0x0
            invoke-interface {v0, p1, v1, v2}, $RENDER_FRAME_HOST_CLASS->d(Ljava/lang/String;Lorg/chromium/url/Origin;Lorg/chromium/base/Callback;)V
            :return
            return-void
        """,
    )

    wrapper.methods.add(method)
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
            invoke-static {v0, p1}, $WEB_CONTENTS_WRAPPER_CLASS->patch_evaluateJavascript(${WEB_CONTENTS_CLASS}Ljava/lang/String;)V
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

private fun patchExtensionPlaybackSpeed(speed: Float) {
    val bits = speed.toBits()
    val smaliConst = if (bits ushr 16 == 0) {
        "const v0, 0x${Integer.toHexString(bits)}"
    } else {
        "const/high16 v0, 0x${Integer.toHexString(bits ushr 16)}"
    }

    mutableClassDefBy(EXTENSION_CLASS).apply {
        methods.removeAll { it.name == "getPlaybackSpeed" }
        addBridge(
            name = "getPlaybackSpeed",
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