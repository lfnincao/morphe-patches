package app.morphe.patches.opera.video.speed

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.io.File
import java.util.zip.ZipFile

fun main(args: Array<String>) {
    val type = args.getOrNull(1)
        ?: "Lorg/chromium/content/browser/JavascriptInjectorImpl;"
    val apk = File(args[0])
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val classDef = dex.classes.firstOrNull { it.type == type } ?: return@forEach
                println("DEX $entry $type methods=${classDef.methods.count()}")
                for (method in classDef.methods) {
                    val calls = method.implementation?.instructions?.mapNotNull { ins ->
                        val ref = ins as? ReferenceInstruction ?: return@mapNotNull null
                        val m = ref.reference as? MethodReference ?: return@mapNotNull null
                        "${m.definingClass}->${m.name}"
                    }?.distinct()?.sorted() ?: emptyList()
                    println(" ${method.name} -> ${calls.take(12)}${if (calls.size > 12) "..." else ""}")
                }
            }
    }
}