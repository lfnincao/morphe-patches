package app.morphe.patches.opera.video.speed

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.io.File
import java.util.zip.ZipFile

fun main(args: Array<String>) {
    require(args.size == 1)
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
                    val t = classDef.type
                    if (!t.contains("JavascriptInjector") && !t.contains("WebContentsImpl")) continue
                    println("CLASS ${entry.name} $t")
                    for (method in classDef.methods) {
                        val outs = method.implementation?.instructions?.mapNotNull { ins ->
                            val ref = ins as? ReferenceInstruction ?: return@mapNotNull null
                            val m = ref.reference as? MethodReference ?: return@mapNotNull null
                            if (m.definingClass == "LJ/N;" || m.name.contains("JavaScript", true)
                                || m.name.contains("Javascript", true)
                            ) {
                                "${m.definingClass}->${m.name}(${m.parameterTypes.joinToString()})"
                            } else null
                        }?.distinct()
                        if (!outs.isNullOrEmpty()) {
                            println("  ${method.name}(${method.parameterTypes.joinToString()}) -> $outs")
                        }
                    }
                }
            }
    }
}