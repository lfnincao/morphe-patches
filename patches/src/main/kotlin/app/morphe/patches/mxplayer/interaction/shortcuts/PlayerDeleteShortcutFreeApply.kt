package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_EXTENSION_FREE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/**
 * Free 3.5.3 delete integrates with the Customize items screen ({@code Luad} + {@code Lcbd;->b()}),
 * not the toolbar-shortcut checklist ({@code Lrbd$a;} / {@code list_shortcuts_eng}).
 */
internal fun applyFreeDeleteShortcutPatches() {
    val ext = DELETE_SHORTCUT_EXTENSION_FREE

    FreeMenuHelperBuildListFingerprint.method.cloneParameters().apply {
        val returnIndex = implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
        addInstructions(
            returnIndex,
            """
            invoke-static {p0, v0}, $ext->appendDeleteCustomizeCandidate(Ljava/lang/Object;Ljava/util/ArrayList;)V
            """.trimIndent(),
        )
    }

    FreeMenuHelperOrientFingerprint.method.cloneParameters().apply {
        val returnIndex = implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
        addInstructions(
            returnIndex,
            """
            invoke-static {p0}, $ext->registerDeleteMenuEntry(Ljava/lang/Object;)V
            """.trimIndent(),
        )
    }

    FreeJy1MenuActionFingerprint.method.cloneParameters().apply {
        val insns = implementation!!.instructions
        // jy1.a(I) copies params from high registers before dispatch; p0/p1 are not valid at entry.
        val hookIndex = insns.indexOfFirst { insn ->
            insn.opcode == Opcode.IGET_OBJECT &&
                insn is ReferenceInstruction &&
                insn.reference.toString().contains("Ljy1;->b:")
        }
        check(hookIndex >= 2) { "jy1.a(I) prologue iget-object Ljy1;.b not found" }
        val continueAt = insns[hookIndex]
        addInstructionsWithLabels(
            hookIndex,
            """
            invoke-static {v1, v0}, $ext->onCustomizeMenuAction(Ljava/lang/Object;I)Z
            move-result v15
            if-eqz v15, :morphe_jy1_continue
            return-void
            """.trimIndent(),
            ExternalLabel("morphe_jy1_continue", continueAt),
        )
    }

    FreeSbdMenuClickFingerprint.method.cloneParameters().apply {
        val insns = implementation!!.instructions
        val continueAt = insns[0]
        // Lsbd;->onClick has a small frame (~10 regs after clone); v15 is out of range (VerifyError).
        val hookResultRegister = findFreeRegister(0)
        addInstructionsWithLabels(
            0,
            """
            invoke-static {p0}, $ext->onCustomizeRowClick(Ljava/lang/Object;)Z
            move-result v$hookResultRegister
            if-eqz v$hookResultRegister, :morphe_sbd_continue
            return-void
            """.trimIndent(),
            ExternalLabel("morphe_sbd_continue", continueAt),
        )
    }
}
