package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_BIT
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_CLEAR_MASK
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_LIST_INDEX
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

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
    val usesStockSet =
        insns.any { insn ->
            insn is ReferenceInstruction &&
                insn.reference.toString().let { ref ->
                    ref.contains("Lo20\$a;") && ref.contains("->s")
                }
        }
    val usesStockClear =
        insns.any { insn ->
            insn is ReferenceInstruction &&
                insn.reference.toString().let { ref ->
                    ref.contains("Lo20\$a;") && ref.contains("->r")
                }
        }
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
    val injectBlock = injectBlockBeforePackedSwitch(insns, packedSwitchIndex)
    val noReturnInInject =
        injectBlock.none { it.opcode == Opcode.RETURN_VOID }
    return listOf(
        (!usesExtensionToggle) to "delete toggle uses stock s/r not onDeleteShortcutToggle",
        (usesStockSet && usesStockClear) to "delete toggle calls Lo20\$a.s and Lo20\$a.r",
        (deleteSetLiteral && deleteClearLiteral) to "delete toggle uses 0x2000 and clear mask literals",
        epiloguePersist to "onCheckedChanged retains shortcuts_flag.3 epilogue",
        noReturnInInject to "delete toggle prologue does not return-void before epilogue",
        deleteMatchesLoopShape to "delete toggle case mirrors Loop (0x8) instruction shape",
    )
}

private fun deleteToggleMatchesLoopShape(insns: List<Instruction>, packedSwitchIndex: Int): Boolean {
    val inject = injectBlockBeforePackedSwitch(insns, packedSwitchIndex)
    val deleteStart =
        inject.indexOfFirst { insn ->
            insn is ReferenceInstruction &&
                insn.reference.toString().contains("Lo20\$a\$a;") &&
                insn.reference.toString().contains("->I:")
        }
    if (deleteStart < 0) return false
    val deleteSlice =
        inject.subList(deleteStart, inject.size).map { normalizeBranchOpcode(it.opcode) }
    val loopSlice = toggleCaseSlice(insns, 0x8) ?: return false
    if (deleteSlice.size != loopSlice.size) return false
    return deleteSlice.zip(loopSlice).all { (a, b) -> a == b }
}

private fun normalizeBranchOpcode(opcode: Opcode): Opcode =
    when (opcode) {
        Opcode.GOTO_16, Opcode.GOTO_32 -> Opcode.GOTO
        else -> opcode
    }

private fun toggleCaseSlice(insns: List<Instruction>, enableLiteral: Int): List<Opcode>? {
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
    val tailStart = insns.subList(enableIndex, insns.size)
    val secondGoto =
        tailStart.withIndex().indexOfFirst { (idx, insn) ->
            idx > 2 &&
                (insn.opcode == Opcode.GOTO ||
                    insn.opcode == Opcode.GOTO_16 ||
                    insn.opcode == Opcode.GOTO_32)
        }
    if (secondGoto < 0) return null
    val end = enableIndex + secondGoto + 1
    return insns.subList(start, end).map { it.opcode }
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