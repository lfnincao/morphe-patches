package app.morphe.patches.mxplayer.interaction.shortcuts

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FourRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import java.io.File
import java.util.zip.ZipFile

/**
 * Fails if any instruction in any concrete method references a register index at or above
 * [com.android.tools.smali.dexlib2.iface.MethodImplementation.registerCount] (ART VerifyError class).
 */
internal fun verifyDexMethodRegisterBounds(apkFile: File): String {
    val violations = mutableListOf<String>()
    readAllDex(apkFile).forEach { dex ->
        dex.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val impl = method.implementation ?: return@forEach
                val limit = impl.registerCount
                impl.instructions.forEachIndexed { index, insn ->
                    instructionRegisters(insn).forEach { reg ->
                        if (reg >= limit) {
                            violations += "${formatMethod(cls, method)} insn[$index] ${insn.opcode} uses v$reg but .registers $limit"
                        }
                    }
                }
            }
        }
    }
    return buildString {
        appendLine("=== dex register bounds verify (${apkFile.name}) ===")
        if (violations.isEmpty()) {
            appendLine("OK  all method instructions use registers in [0, registerCount)")
            appendLine("PASSED")
        } else {
            violations.distinct().take(40).forEach { appendLine("VIOLATION  $it") }
            if (violations.size > 40) {
                appendLine("VIOLATION  ... and ${violations.size - 40} more")
            }
            appendLine("FAILED")
        }
    }.also { report ->
        require(report.contains("PASSED")) { report }
    }
}

internal fun instructionRegisters(insn: Instruction): List<Int> =
    when (insn) {
        is RegisterRangeInstruction ->
            (insn.startRegister until insn.startRegister + insn.registerCount).toList()
        is FiveRegisterInstruction ->
            when (insn.registerCount) {
                0 -> emptyList()
                1 -> listOf(insn.registerC)
                2 -> listOf(insn.registerC, insn.registerD)
                3 -> listOf(insn.registerC, insn.registerD, insn.registerE)
                4 -> listOf(insn.registerC, insn.registerD, insn.registerE, insn.registerF)
                5 -> listOf(insn.registerC, insn.registerD, insn.registerE, insn.registerF, insn.registerG)
                else -> emptyList()
            }
        is FourRegisterInstruction -> listOf(insn.registerA, insn.registerB, insn.registerC, insn.registerD)
        is ThreeRegisterInstruction -> listOf(insn.registerA, insn.registerB, insn.registerC)
        is TwoRegisterInstruction -> listOf(insn.registerA, insn.registerB)
        is OneRegisterInstruction -> listOf(insn.registerA)
        else -> emptyList()
    }

private fun formatMethod(cls: ClassDef, method: Method): String {
    val params = method.parameters.joinToString(",") { it.type }
    return "${cls.type}->${method.name}($params)"
}

private fun readAllDex(apk: File): List<DexBackedDexFile> =
    ZipFile(apk).use { zip ->
        zip.stream()
            .filter { !it.isDirectory && it.name.matches(Regex("classes\\d*\\.dex")) }
            .map { entry ->
                val bytes = zip.getInputStream(entry).readBytes()
                DexBackedDexFile.fromInputStream(Opcodes.getDefault(), bytes.inputStream())
            }
            .toList()
    }