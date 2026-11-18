/*
 * One-off recon: list stock Opera call sites for J.N.Mhlly_d7 and the iget-long field used.
 * Usage: OperaMhllyD7CallSiteReconKt /path/to/stock.apk
 */
package app.morphe.patches.opera.video.speed

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.Opcode
import java.io.File
import java.util.zip.ZipFile

private const val JNI_CLASS = "LJ/N;"
private const val JNI_METHOD = "Mhlly_d7"

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: OperaMhllyD7CallSiteReconKt <apk>" }
    val apk = File(args[0]).canonicalFile
    val sites = mutableListOf<String>()
  ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                for (classDef in dex.classes) {
                    for (method in classDef.methods) {
                        val impl = method.implementation ?: continue
                        val instructions = impl.instructions.toList()
                        instructions.forEachIndexed { index, ins ->
                            val refIns = ins as? ReferenceInstruction ?: return@forEachIndexed
                            val methodRef = refIns.reference as? MethodReference ?: return@forEachIndexed
                            if (methodRef.definingClass != JNI_CLASS || methodRef.name != JNI_METHOD) {
                                return@forEachIndexed
                            }
                            val ptrSource = findPointerLoad(instructions, index)
                            sites += buildString {
                                append(entry.name)
                                append(" ")
                                append(classDef.type)
                                append("->")
                                append(method.name)
                                append(" #")
                                append(index)
                                append(" ptr=")
                                append(ptrSource ?: "?")
                            }
                        }
                    }
                }
            }
    }
    sites.sorted().forEach { println(it) }
    println("total=${sites.size}")
    if (sites.isEmpty()) {
        println("(no Mhlly_d7 invocations found — stock may route EvaluateJavaScript elsewhere)")
    }
}

private fun findPointerLoad(
    instructions: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>,
    invokeIndex: Int,
): String? {
    for (i in invokeIndex - 1 downTo maxOf(0, invokeIndex - 12)) {
        when (instructions[i].opcode) {
            Opcode.IGET, Opcode.IGET_WIDE, Opcode.IGET_OBJECT -> {
                val ref = instructions[i] as ReferenceInstruction
                val field = ref.reference as? FieldReference ?: continue
                if (field.type == "J" || field.type == "Ljava/lang/Long;") {
                    return "${field.definingClass}->${field.name}:${field.type}"
                }
            }
            Opcode.MOVE_RESULT_WIDE, Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16 -> continue
            Opcode.CONST_WIDE, Opcode.CONST_WIDE_16, Opcode.CONST_WIDE_32 -> continue
            else -> if (instructions[i].opcode != Opcode.NOP) break
        }
    }
    for (i in invokeIndex - 1 downTo maxOf(0, invokeIndex - 12)) {
        val ins = instructions[i]
        if (ins.opcode == Opcode.IGET_WIDE) {
            val field = (ins as ReferenceInstruction).reference as FieldReference
            return "${field.definingClass}->${field.name}:${field.type}"
        }
        if (ins.opcode == Opcode.IGET) {
            val field = (ins as ReferenceInstruction).reference as FieldReference
            if (field.type == "J") {
                return "${field.definingClass}->${field.name}:${field.type}"
            }
        }
    }
    return null
}