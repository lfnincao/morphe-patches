package app.morphe.patches.mxplayer.interaction.shortcuts

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import java.io.File
import java.util.zip.ZipFile

private const val EXTENSION_PREFIX = "Lapp/morphe/extension/"

private data class MethodId(
    val definingClass: String,
    val name: String,
    val descriptor: String,
) {
    fun display(): String = "$definingClass->$name$descriptor"
}

private val INVOKE_OPCODES =
    setOf(
        Opcode.INVOKE_STATIC,
        Opcode.INVOKE_VIRTUAL,
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_INTERFACE,
        Opcode.INVOKE_STATIC_RANGE,
        Opcode.INVOKE_VIRTUAL_RANGE,
        Opcode.INVOKE_DIRECT_RANGE,
        Opcode.INVOKE_INTERFACE_RANGE,
    )

/**
 * Scans every DEX in the patched APK for invoke-* targeting [EXTENSION_PREFIX] and asserts each
 * exact class + name + descriptor is defined in the APK (catches patch smali / extension signature skew).
 */
internal fun verifyMxPlayerExtensionInvokes(apkFile: File): String {
    val defined = mutableSetOf<MethodId>()
    val invokes = linkedMapOf<MethodId, String>()

    ZipFile(apkFile).use { zip ->
        zip.stream()
            .filter { !it.isDirectory && it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val bytes = zip.getInputStream(entry).readBytes()
                val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), bytes.inputStream())
                for (classDef in dex.classes) {
                    if (classDef.type.startsWith(EXTENSION_PREFIX)) {
                        for (method in classDef.methods) {
                            defined += method.toMethodId(classDef.type)
                        }
                    }
                    for (method in classDef.methods) {
                        val impl = method.implementation ?: continue
                        for (instruction in impl.instructions) {
                            if (instruction.opcode !in INVOKE_OPCODES) continue
                            val ref = (instruction as ReferenceInstruction).reference
                            if (ref !is MethodReference) continue
                            if (!ref.definingClass.startsWith(EXTENSION_PREFIX)) continue
                            val id = ref.toMethodId()
                            invokes.putIfAbsent(id, entry.name)
                        }
                    }
                }
            }
    }

    val lines = buildString {
        appendLine("=== extension invoke verify (${apkFile.name}) ===")
        appendLine("Defined extension methods: ${defined.size}")
        appendLine("Distinct extension invoke targets: ${invokes.size}")
        appendLine()
        val missing = mutableListOf<MethodId>()
        invokes.keys.sortedWith(compareBy({ it.definingClass }, { it.name }, { it.descriptor })).forEach { id ->
            val dex = invokes[id] ?: "?"
            val status = if (id in defined) "OK" else "MISSING"
            if (status == "MISSING") missing += id
            appendLine("$status  [$dex]  ${id.display()}")
        }
        if (missing.isNotEmpty()) {
            appendLine()
            appendLine("FAILED: ${missing.size} invoke target(s) not defined in APK DEX")
        } else {
            appendLine()
            appendLine("PASSED: all extension invoke targets resolved in APK DEX")
        }
    }

    require(lines.contains("PASSED:")) {
        "Extension invoke verification failed:\n$lines"
    }
    return lines
}

private fun Method.toMethodId(definingClass: String): MethodId =
    MethodId(
        definingClass = definingClass,
        name = name,
        descriptor = "(${parameterTypes.joinToString("") { it }})$returnType",
    )

private fun MethodReference.toMethodId(): MethodId =
    MethodId(
        definingClass = definingClass,
        name = name,
        descriptor = "(${parameterTypes.joinToString("") { it }})$returnType",
    )