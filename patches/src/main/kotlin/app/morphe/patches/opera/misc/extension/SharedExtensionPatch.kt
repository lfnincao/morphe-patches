package app.morphe.patches.opera.misc.extension

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefByOrNull
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patches.all.misc.extension.SHARED_UTILS_EXTENSION_CLASS
import app.morphe.patches.all.misc.extension.sharedExtensionPatch
import app.morphe.patches.opera.misc.extension.hooks.operaApplicationOnCreateHook
import com.android.tools.smali.dexlib2.util.MethodUtil

private const val OPERA_APPLICATION_CLASS_TYPE = "Lcom/opera/android/OperaApplication;"

val operaSharedExtensionPatch = patch {
    dependsOn(
        sharedExtensionPatch(
            listOf("opera", "shared"),
            operaApplicationOnCreateHook,
        ),
    )

    finalize {
        var type: String? = OPERA_APPLICATION_CLASS_TYPE
        while (type != null) {
            val classDef = classDefByOrNull(type)
                ?: throw PatchException("Could not find Opera Application onCreate for extension context")
            val onCreate = classDef.methods.firstOrNull {
                it.name == "onCreate" && it.parameterTypes.isEmpty() && it.returnType == "V"
            }
            if (onCreate != null) {
                mutableClassDefBy(classDef).methods.first {
                    MethodUtil.methodSignaturesMatch(it, onCreate)
                }.addInstruction(
                    0,
                    "invoke-static/range { p0 .. p0 }, $SHARED_UTILS_EXTENSION_CLASS->setContext(Landroid/content/Context;)V",
                )
                break
            }
            type = classDef.superclass
        }
    }
}