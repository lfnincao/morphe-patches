/*
 * Usage: OperaWebContentsJsReconKt <apk>
 */
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
                    if (!classDef.type.contains("WebContents")) continue
                    for (method in classDef.methods) {
                        if (!method.name.contains("evaluate", ignoreCase = true)
                            && !method.name.contains("JavaScript", ignoreCase = true)
                            && !method.name.contains("Javascript", ignoreCase = true)
                        ) {
                            continue
                        }
                        val calls = method.implementation?.instructions?.mapNotNull { ins ->
                            val ref = ins as? ReferenceInstruction ?: return@mapNotNull null
                            val m = ref.reference as? MethodReference ?: return@mapNotNull null
                            "${m.definingClass}->${m.name}${m.parameterTypes.joinToString("", "(", ")V".take(1))}"
                        }?.filter { it.contains("J/N") || it.contains("Javascript") || it.contains("WebContents") }
                        if (!calls.isNullOrEmpty()) {
                            println("${entry.name} ${classDef.type}->${method.name} :: $calls")
                        }
                    }
                }
            }
    }
}