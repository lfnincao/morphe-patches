package app.morphe.patches.opera.misc.extension

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patches.all.misc.extension.sharedExtensionPatch
import app.morphe.patches.opera.misc.extension.hooks.OperaApplicationOnCreateFingerprint
import app.morphe.patches.opera.misc.extension.hooks.operaApplicationOnCreateHook
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.util.MethodUtil

private const val OPERA_PROCESS_GUARD_CLASS = "Lapp/morphe/extension/opera/OperaProcessGuard;"
private const val SHARED_UTILS_CLASS = "Lapp/morphe/extension/shared/Utils;"

/**
 * Full Opera extension with main-process context init (for future settings UI patches).
 * Do not use for bytecode-only patches; use [operaExtensionDexPatch] instead.
 */
val operaSharedExtensionPatch = patch {
    dependsOn(
        sharedExtensionPatch(
            listOf("opera", "shared"),
            operaApplicationOnCreateHook,
        ),
    )

    finalize {
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
        check(replaced) { "OperaApplication.onCreate has no Utils.setContext hook to guard" }
    }
}