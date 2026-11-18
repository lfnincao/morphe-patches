package app.morphe.patches.opera.video.speed

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.Opcode
import java.io.File
import java.util.zip.ZipFile

fun main(args: Array<String>) {
    val apk = File(args[0])
    val type = args.getOrNull(1) ?: "Lorg/chromium/content/browser/webcontents/WebContentsImpl;"
    val method = args.getOrNull(2) ?: "R0"
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val classDef = dex.classes.firstOrNull { it.type == type } ?: return@forEach
                val m = classDef.methods.firstOrNull { it.name == method } ?: return@forEach
                println("$entry $type->$method prologue:")
                m.implementation?.instructions?.take(8)?.forEach { ins ->
                    println("  ${ins.opcode} $ins")
                    if (ins.opcode == Opcode.IGET_WIDE || ins.opcode == Opcode.IGET) {
                        val f = (ins as ReferenceInstruction).reference as FieldReference
                        println("    field ${f.definingClass}->${f.name}:${f.type}")
                    }
                }
            }
    }
}