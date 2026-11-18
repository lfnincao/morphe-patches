package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_BIT
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_CLEAR_MASK
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_LIST_INDEX
import app.morphe.patches.mxplayer.shared.Constants.LO20_ADAPTER_BITWISE_DESCRIPTOR
import app.morphe.patches.mxplayer.shared.Constants.LO20_ADAPTER_CLASS
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Bind prologue: delete row uses extension; other rows fall through to packed-switch. */
internal fun verifyCustomizeBindInjectGates(insns: List<Instruction>): List<Pair<Boolean, String>> {
    val packedSwitchIndex = insns.indexOfFirst { it.opcode == Opcode.PACKED_SWITCH }
    if (packedSwitchIndex < 0) return listOf(false to "packed-switch missing")
    val hookIndex =
        insns.indexOfFirst {
            it is ReferenceInstruction && it.reference.toString().contains("bindDeleteRowChecked")
        }
    if (hookIndex < 0) return listOf(false to "bindDeleteRowChecked hook missing")
    val returnAfterHook = insns.getOrNull(hookIndex + 1)?.opcode == Opcode.RETURN_VOID
    val injectBlock = injectBlockBeforePackedSwitch(insns, packedSwitchIndex)
    return listOf(
        returnAfterHook to "delete bind hook returns after extension (non-delete use packed-switch)",
        !injectBlockHasGoto(injectBlock) to "inject prologue has no goto",
        injectBranchesAreIfNeOnly(injectBlock) to "inject branches use if-ne only",
    )
}

/**
 * Toggle prologue: index 12 uses stock {@code Lo20$a.s}/{@code r} + epilogue like Loop (0x8).
 */
internal fun verifyCustomizeToggleInjectGates(insns: List<Instruction>): List<Pair<Boolean, String>> {
    val packedSwitchIndex = insns.indexOfFirst { it.opcode == Opcode.PACKED_SWITCH }
    if (packedSwitchIndex < 0) return listOf(false to "packed-switch missing")
    val usesExtensionToggle =
        insns.any {
            it is ReferenceInstruction && it.reference.toString().contains("onDeleteShortcutToggle")
        }
    val usesStockSet = invokeMatches(insns, "s")
    val usesStockClear = invokeMatches(insns, "r")
    val deleteSetLiteral =
        insns.any { insn ->
            insn.opcode == Opcode.CONST_16 &&
                (insn as NarrowLiteralInstruction).narrowLiteral == DELETE_SHORTCUT_BIT
        }
    val deleteClearLiteral =
        insns.any { insn ->
            insn.opcode == Opcode.CONST_16 &&
                (insn as NarrowLiteralInstruction).narrowLiteral == DELETE_SHORTCUT_CLEAR_MASK
        }
    val epiloguePersist =
        insns.any { insn ->
            insn.opcode == Opcode.CONST_STRING &&
                insn is ReferenceInstruction &&
                insn.reference.toString().contains("shortcuts_flag.3")
        }
    val deleteMatchesLoopShape = deleteToggleMatchesLoopShape(insns, packedSwitchIndex)
    val gotoTargetsMatchLoop = deleteAndLoopShareEpilogueGotoTarget(insns, packedSwitchIndex)
    val injectBlock = injectBlockBeforePackedSwitch(insns, packedSwitchIndex)
    val noReturnInInject =
        injectBlock.none { it.opcode == Opcode.RETURN_VOID }
    return listOf(
        (!usesExtensionToggle) to "delete toggle uses stock s/r not onDeleteShortcutToggle",
        (usesStockSet && usesStockClear) to "delete toggle calls Lo20\$a.s and Lo20\$a.r",
        (deleteSetLiteral && deleteClearLiteral) to "delete toggle uses 0x2000 and clear mask literals",
        epiloguePersist to "onCheckedChanged retains shortcuts_flag.3 epilogue",
        noReturnInInject to "delete toggle prologue does not return-void before epilogue",
        deleteMatchesLoopShape to "delete toggle case mirrors Loop (0x8) except enable/clear literals",
        gotoTargetsMatchLoop to "delete goto targets match Loop goto target (LlV.A sget)",
    )
}

private fun deleteToggleMatchesLoopShape(insns: List<Instruction>, packedSwitchIndex: Int): Boolean {
    val deleteBounds = toggleCaseBounds(insns, DELETE_SHORTCUT_BIT) ?: return false
    val deleteSlice = toggleCaseInstructionSlice(insns, deleteBounds.first, deleteBounds.second)
    val loopBounds = toggleCaseBounds(insns, 0x8) ?: return false
    val loopSlice = toggleCaseInstructionSlice(insns, loopBounds.first, loopBounds.second)
    if (deleteSlice.size != loopSlice.size) return false
    return deleteSlice.zip(loopSlice).all { (a, b) -> shapesMatchAllowingToggleLiterals(a, b) }
}

private fun deleteAndLoopShareEpilogueGotoTarget(insns: List<Instruction>, packedSwitchIndex: Int): Boolean {
    val epilogueIndex =
        insns.indexOfFirst { insn ->
            insn.opcode == Opcode.SGET_OBJECT &&
                insn is ReferenceInstruction &&
                insn.reference.toString().contains("LlV;") &&
                insn.reference.toString().contains("->A:")
        }
    if (epilogueIndex < 0) return false
    val deleteBounds = toggleCaseBounds(insns, DELETE_SHORTCUT_BIT) ?: return false
    val loopBounds = toggleCaseBounds(insns, 0x8) ?: return false
    val deleteGotos = gotoTargetIndices(insns, deleteBounds.first, deleteBounds.second)
    val loopGotos = gotoTargetIndices(insns, loopBounds.first, loopBounds.second)
    if (deleteGotos.isEmpty() || loopGotos.isEmpty()) return false
    return deleteGotos.all { it == epilogueIndex } &&
        loopGotos.all { it == epilogueIndex } &&
        deleteGotos.toSet() == loopGotos.toSet()
}

private fun normalizeBranchOpcode(opcode: Opcode): Opcode =
    when (opcode) {
        Opcode.GOTO_16, Opcode.GOTO_32 -> Opcode.GOTO
        else -> opcode
    }

private fun toggleCaseBounds(insns: List<Instruction>, enableLiteral: Int): Pair<Int, Int>? {
    val enableIndex =
        insns.indexOfFirst { insn ->
            insn.opcode == Opcode.CONST_16 &&
                (insn as NarrowLiteralInstruction).narrowLiteral == enableLiteral
        }
    if (enableIndex < 0) return null
    val start =
        insns.subList(0, enableIndex).indexOfLast { insn ->
            insn is ReferenceInstruction &&
                insn.reference.toString().contains("Lo20\$a\$a;") &&
                insn.reference.toString().contains("->I:")
        }
    if (start < 0) return null
    val caseTail = insns.subList(enableIndex, insns.size)
    val firstGotoRel =
        caseTail.indexOfFirst { insn ->
            insn.opcode == Opcode.GOTO ||
                insn.opcode == Opcode.GOTO_16 ||
                insn.opcode == Opcode.GOTO_32
        }
    if (firstGotoRel < 0) return null
    val afterFirstGoto = caseTail.subList(firstGotoRel + 1, caseTail.size)
    val secondGotoRel =
        afterFirstGoto.indexOfFirst { insn ->
            insn.opcode == Opcode.GOTO ||
                insn.opcode == Opcode.GOTO_16 ||
                insn.opcode == Opcode.GOTO_32
        }
    if (secondGotoRel < 0) return null
    val end = enableIndex + firstGotoRel + 1 + secondGotoRel + 1
    return start to end
}

private fun toggleCaseInstructionSlice(insns: List<Instruction>, start: Int, end: Int): List<String> =
    insns.subList(start, end).map { instructionShapeToken(it) }

private fun gotoTargetIndices(insns: List<Instruction>, start: Int, end: Int): List<Int> {
    val offsetToIndex = instructionOffsetMap(insns)
    val results = mutableListOf<Int>()
    insns.subList(start, end).forEachIndexed { rel, insn ->
        if (
            insn.opcode != Opcode.GOTO &&
            insn.opcode != Opcode.GOTO_16 &&
            insn.opcode != Opcode.GOTO_32
        ) {
            return@forEachIndexed
        }
        val gotoIndex = start + rel
        var codeOffset = 0
        for (i in 0 until gotoIndex) {
            codeOffset += insns[i].codeUnits
        }
        val targetCodeOffset = codeOffset + (insn as OffsetInstruction).codeOffset
        val targetIndex = offsetToIndex[targetCodeOffset] ?: return emptyList()
        results += targetIndex
    }
    return results
}

private fun instructionOffsetMap(insns: List<Instruction>): Map<Int, Int> {
    val map = mutableMapOf<Int, Int>()
    var codeOffset = 0
    insns.forEachIndexed { index, insn ->
        map[codeOffset] = index
        codeOffset += insn.codeUnits
    }
    return map
}

private fun shapesMatchAllowingToggleLiterals(deleteToken: String, loopToken: String): Boolean {
    if (deleteToken == "CONST_16" && loopToken == "CONST_16") return true
    return deleteToken == loopToken
}

private fun invokeMatches(insns: List<Instruction>, methodName: String): Boolean =
    insns.any { insn ->
        if (insn !is ReferenceInstruction || insn.reference !is MethodReference) return@any false
        val ref = insn.reference as MethodReference
        ref.definingClass == LO20_ADAPTER_CLASS &&
            ref.name == methodName &&
            "(${ref.parameterTypes.joinToString("") { it }})${ref.returnType}" == LO20_ADAPTER_BITWISE_DESCRIPTOR
    }

private fun instructionShapeToken(insn: Instruction): String {
    if (insn.opcode == Opcode.CONST_16) {
        return "CONST_16"
    }
    if (insn is ReferenceInstruction && insn.reference is MethodReference) {
        val ref = insn.reference as MethodReference
        val descriptor = "(${ref.parameterTypes.joinToString("") { it }})${ref.returnType}"
        return "${normalizeBranchOpcode(insn.opcode)}:${ref.definingClass}->${ref.name}$descriptor"
    }
    if (insn is ReferenceInstruction) {
        return "${normalizeBranchOpcode(insn.opcode)}:${insn.reference}"
    }
    return normalizeBranchOpcode(insn.opcode).toString()
}

private fun injectBlockBeforePackedSwitch(insns: List<Instruction>, packedSwitchIndex: Int): List<Instruction> {
    val injectStart =
        insns.subList(0, packedSwitchIndex).indexOfLast { insn ->
            insn.opcode == Opcode.CONST_16 &&
                (insn as NarrowLiteralInstruction).narrowLiteral == DELETE_SHORTCUT_LIST_INDEX
        }
    return if (injectStart >= 0) insns.subList(injectStart, packedSwitchIndex) else emptyList()
}

private fun injectBlockHasGoto(block: List<Instruction>): Boolean =
    block.any { insn ->
        insn.opcode == Opcode.GOTO || insn.opcode == Opcode.GOTO_16 || insn.opcode == Opcode.GOTO_32
    }

private fun injectBranchesAreIfNeOnly(block: List<Instruction>): Boolean {
    val branches =
        block.filter { insn ->
            insn.opcode == Opcode.IF_EQ ||
                insn.opcode == Opcode.IF_NE ||
                insn.opcode == Opcode.IF_EQZ ||
                insn.opcode == Opcode.IF_NEZ
        }
    return branches.isEmpty() || branches.all { it.opcode == Opcode.IF_NE }
}