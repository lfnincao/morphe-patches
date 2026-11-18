package app.morphe.patches.opera.video.speed

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.Opcode
import java.io.File
import java.util.zip.ZipFile

fun main(args: Array<String>) {
    val apk = File(args[0])
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
                        val instructions = method.implementation?.instructions?.toList() ?: continue
                        instructions.forEachIndexed { index, ins ->
                            val ref = ins as? ReferenceInstruction ?: return@forEachIndexed
                            val m = ref.reference as? MethodReference ?: return@forEachIndexed
                            if (m.definingClass != "LJ/N;" || m.name != "Mhlly_d7") return@forEachIndexed
                            val ptr = findIgwideField(instructions, index)
                            println(
                                "${entry.name} ${classDef.type}->${method.name} #$index ptr=$ptr",
                            )
                        }
                    }
                }
            }
    }
}

private fun findIgwideField(
    instructions: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>,
    invokeIndex: Int,
): String? {
    for (i in invokeIndex - 1 downTo maxOf(0, invokeIndex - 20)) {
        val ins = instructions[i]
        if (ins.opcode == Opcode.IGET_WIDE) {
            val f = (ins as ReferenceInstruction).reference as FieldReference
            return "${f.definingClass}->${f.name}:J"
        }
        if (ins.opcode == Opcode.CHECK_CAST) continue
        if (ins.opcode == Opcode.IGET_OBJECT) continue
        if (ins.opcode.name.startsWith("MOVE")) continue
        if (ins.opcode == Opcode.INSTANCE_OF) continue
    }
    return null
}