package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_EXTENSION_FREE
import com.android.tools.smali.dexlib2.Opcode

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
        val continueAt = getInstruction(0)
        addInstructionsWithLabels(
            0,
            """
            invoke-static {p0, p1}, $ext->onCustomizeMenuAction(Ljava/lang/Object;I)Z
            move-result v0
            if-eqz v0, :morphe_jy1_continue
            return-void
            """.trimIndent(),
            app.morphe.patcher.util.smali.ExternalLabel("morphe_jy1_continue", continueAt),
        )
    }

    FreeSbdMenuClickFingerprint.method.cloneParameters().apply {
        val continueAt = getInstruction(0)
        addInstructionsWithLabels(
            0,
            """
            invoke-static {p0}, $ext->onCustomizeRowClick(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :morphe_sbd_continue
            return-void
            """.trimIndent(),
            app.morphe.patcher.util.smali.ExternalLabel("morphe_sbd_continue", continueAt),
        )
    }
}
