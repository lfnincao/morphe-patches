/*
 * Verifies patched Opera DEX for UniversalVideoPlaybackSpeedPatch.getPlaybackSpeed().
 *
 * Usage: OperaPlaybackSpeedDexVerifyKt <patched.apk>
 */
package app.morphe.patches.opera.video.speed

import app.morphe.patcher.dex.SdkDexVerifier
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.Opcode
import java.io.File
import java.util.zip.ZipFile

private const val EXTENSION_TYPE =
    "Lapp/morphe/extension/opera/patches/UniversalVideoPlaybackSpeedPatch;"

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: OperaPlaybackSpeedDexVerifyKt <patched.apk>" }
    val apk = File(args[0]).canonicalFile
    require(apk.isFile) { "Missing APK: $apk" }

    val dexFiles = mutableListOf<File>()
    val smaliOut = StringBuilder()
    var found = false
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val bytes = zip.getInputStream(entry).readBytes()
                val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), bytes.inputStream())
                val classDef = dex.classes.firstOrNull { it.type == EXTENSION_TYPE } ?: return@forEach
                found = true
                val method = classDef.methods.firstOrNull { it.name == "getPlaybackSpeed" }
                    ?: error("getPlaybackSpeed not found in $EXTENSION_TYPE")
                val impl = method.implementation
                    ?: error("getPlaybackSpeed has no implementation")
                val instructions = impl.instructions.toList()
                check(instructions.size >= 2) {
                    "getPlaybackSpeed too short (${instructions.size} insns): $instructions"
                }
                val first = instructions.first()
                val last = instructions.last()
                check(first.opcode == Opcode.CONST || first.opcode == Opcode.CONST_HIGH16) {
                    "First instruction must load a float constant, got ${first.opcode}"
                }
                check(last.opcode == Opcode.RETURN) {
                    "Last instruction must be return, got ${last.opcode}"
                }
                smaliOut.appendLine("# ${entry.name} :: $EXTENSION_TYPE->getPlaybackSpeed()")
                instructions.forEach { ins -> smaliOut.appendLine(instructionToSmali(ins)) }
                smaliOut.appendLine()
                val tmp = File.createTempFile("opera-verify-", ".dex")
                tmp.writeBytes(bytes)
                dexFiles += tmp
            }
    }
    check(found) { "Extension class not in patched APK" }

    listOf(
        "patch_evaluateJavascript" to "Lcom/opera/android/browser/WebContentsWrapper;",
        "patch_executePlaybackSpeedScript" to "Lcom/opera/android/browser/chromium/OperaWebContentsDelegate;",
    ).forEach { (name, type) ->
        verifyHelperMethod(apk, type, name, smaliOut)
    }

    val sdk = System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME required")
    val verifier = SdkDexVerifier(File(sdk))
    dexFiles.forEach { verifier.verifyDexFile(it) }
    println("ART/SDK DEX verification passed for ${dexFiles.size} file(s) containing extension.")
    println("--- getPlaybackSpeed() smali ---")
    print(smaliOut)
}

private fun instructionToSmali(ins: Instruction): String {
    return when (ins.opcode) {
        Opcode.CONST, Opcode.CONST_HIGH16 -> {
            val reg = (ins as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA
            val lit = (ins as com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction).narrowLiteral
            "${ins.opcode.name.lowercase().replace('_', '/')} v$reg, 0x${Integer.toHexString(lit)}"
        }
        Opcode.RETURN -> {
            val reg = (ins as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA
            "return v$reg"
        }
        else -> ins.opcode.toString()
    }
}

private fun verifyHelperMethod(apk: File, type: String, name: String, out: StringBuilder) {
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val classDef = dex.classes.firstOrNull { it.type == type } ?: return@forEach
                val method = classDef.methods.firstOrNull { it.name == name }
                    ?: error("$type->$name not found in patched APK")
                val instructions = method.implementation?.instructions?.toList()
                    ?: error("$type->$name has no implementation")
                check(instructions.isNotEmpty()) { "$type->$name is empty" }
                out.appendLine("# ${entry.name} :: $type->$name")
                instructions.forEach { ins -> out.appendLine("  $ins") }
                out.appendLine()
            }
    }
}