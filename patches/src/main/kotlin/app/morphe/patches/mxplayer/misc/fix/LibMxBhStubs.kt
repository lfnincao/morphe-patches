package app.morphe.patches.mxplayer.misc.fix

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.getReference
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.resourceFile
import app.morphe.util.inputStreamFromBundledResource
import app.morphe.patcher.util.numberOfParameterRegistersLogical
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

internal const val MX_BH_CLASS = "Lcom/ddx/axx/axx/axx/BH;"
internal const val MX_AS_CLASS = "Lcom/ddx/axx/axx/axx/AS;"
internal const val MX_AG_CLASS = "Lcom/ddx/axx/axx/axx/AG;"

private const val MX_BH_STUBS_BUNDLE = "mxplayer/native"

private const val LIB_MX_BH_ARM64_PATH = "lib/arm64-v8a/libmx-bh.so"
private const val LIB_MX_BH_ARM64_STUB = "lib/arm64-v8a/libmx-bh.so"

internal fun disableLibMxBhLoadingAndNative() {
    skipLvOnCreateMxBhLoad()

    listOf(MX_BH_CLASS, MX_AS_CLASS).forEach { type ->
        mutableClassDefBy(type).methods.first { it.name == "<clinit>" }.apply {
            addInstructions(0, "return-void")
        }
    }

    listOf(MX_BH_CLASS, MX_AS_CLASS, MX_AG_CLASS).forEach { type ->
        mutableClassDefBy(type).stubAllNativeMethods()
    }
}

internal fun replaceLibMxBhWithStubInApk() {
    val stubBytes = inputStreamFromBundledResource(MX_BH_STUBS_BUNDLE, LIB_MX_BH_ARM64_STUB)?.use { it.readBytes() }
        ?: throw PatchException("Missing bundled libmx-bh stub: $LIB_MX_BH_ARM64_STUB")
    resourceFile(LIB_MX_BH_ARM64_PATH, copy = false).writeBytes(stubBytes)
}

private fun skipLvOnCreateMxBhLoad() {
    val fingerprint = MxLvOnCreateMxBhStringFingerprint
    val stringIndex = fingerprint.instructionMatches.first().index
    val method = fingerprint.method
    val loadLibraryIndex = method.instructions.withIndex()
        .drop(stringIndex)
        .first { (_, instruction) ->
            instruction.opcode == Opcode.INVOKE_STATIC &&
                instruction.getReference<MethodReference>()?.let { reference ->
                    reference.definingClass == "Ljava/lang/System;" && reference.name == "loadLibrary"
                } == true
        }
        .index
    method.replaceInstruction(loadLibraryIndex, "nop")
}

private fun MutableClass.stubAllNativeMethods() {
    methods.filter { AccessFlags.NATIVE.isSet(it.accessFlags) }.toList().forEach { nativeMethod ->
        methods.remove(nativeMethod)
        val parameters = nativeMethod.parameters.map { parameter ->
            ImmutableMethodParameter(parameter.type, parameter.annotations, parameter.name)
        }
        val accessFlags = nativeMethod.accessFlags and AccessFlags.NATIVE.value.inv()
        val registerCount = maxOf(nativeMethod.numberOfParameterRegistersLogical + 2, 3)
        val stub = ImmutableMethod(
            nativeMethod.definingClass,
            nativeMethod.name,
            parameters,
            nativeMethod.returnType,
            accessFlags,
            null,
            null,
            MutableMethodImplementation(registerCount),
        ).toMutable()
        stub.addInstructions(0, returnSmaliFor(nativeMethod))
        methods.add(stub)
    }
}

private fun returnSmaliFor(method: MutableMethod): String =
    when (method.returnType) {
        "V" -> "return-void"
        "Z", "I", "B" -> """
            const/4 v0, 0x0
            return v0
        """.trimIndent()
        "S", "C" -> """
            const/16 v0, 0x0
            return v0
        """.trimIndent()
        "[B" -> """
            const/4 v0, 0x0
            new-array v0, v0, [B
            return-object v0
        """.trimIndent()
        else ->
            when (method.returnType.first()) {
                'L', '[' -> """
                    const/4 v0, 0x0
                    return-object v0
                """.trimIndent()
                'J' -> """
                    const-wide v0, 0x0
                    return-wide v0
                """.trimIndent()
                'D' -> """
                    const-wide v0, 0x0
                    return-wide v0
                """.trimIndent()
                'F' -> """
                    const/4 v0, 0x0
                    return v0
                """.trimIndent()
                else -> throw PatchException("Unsupported native stub return type: ${method.returnType}")
            }
    }