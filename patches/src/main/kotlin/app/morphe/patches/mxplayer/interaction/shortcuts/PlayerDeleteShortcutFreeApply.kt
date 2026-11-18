package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.mxplayer.shared.Constants
import app.morphe.patches.mxplayer.shared.Constants.ACTIVITY_SCREEN_CLASS
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_BIT_FREE
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_EXTENSION_FREE
import app.morphe.patches.mxplayer.shared.Constants.FREE_DELETE_DIALOG_INNER_CLASS
import app.morphe.patches.mxplayer.shared.Constants.FREE_TOOLBAR_MODEL_CLASS
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableField

internal fun applyFreeDeleteShortcutPatches() {
    val ext = DELETE_SHORTCUT_EXTENSION_FREE
    val cacheFieldName = "patchDeleteShortcutB0j"

    mutableClassDefBy(ACTIVITY_SCREEN_CLASS).fields.add(
        ImmutableField(
            ACTIVITY_SCREEN_CLASS,
            cacheFieldName,
            FREE_TOOLBAR_MODEL_CLASS,
            AccessFlags.PUBLIC.value,
            null,
            null,
            null,
        ).toMutable(),
    )

    FreeCustomizeItemsOnBindFingerprint.method.cloneParameters().patchFreeCustomizeBindDeleteRow(ext)
    FreeCustomizeItemsToggleFingerprint.method.cloneParameters().patchFreeCustomizeToggleDeleteRow(ext)
    FreeMenuMoreShortcutsPanelFingerprint.method.cloneParameters().apply {
        addInstructions(
            indexOfLastReturnVoid(),
            """
            invoke-static {p0}, $ext->ensureShortcutsPanelVisible(Ljava/lang/Object;)V
            """.trimIndent(),
        )
    }
    FreeActivityScreenBuildShortcutsFingerprint.method.cloneParameters().apply {
        addInstructions(
            indexOfLastReturnVoid(),
            """
            invoke-static {p0}, $ext->syncToolbarShortcut(Ljava/lang/Object;)V
            """.trimIndent(),
        )
    }
    FreeShortcutAdapterOnClickFingerprint.method.cloneParameters().apply {
        val continueInstruction = getInstruction(0)
        addInstructionsWithLabels(
            0,
            """
            invoke-static {p0}, $ext->onToolbarShortcutClick(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :morphe_onclick_continue
            return-void
            """.trimIndent(),
            ExternalLabel("morphe_onclick_continue", continueInstruction),
        )
    }
    FreeShortcutAdapterCreateViewHolderFingerprint.method.cloneParameters().apply {
        addInstructions(
            0,
            """
            invoke-static {p1}, $ext->remapShortcutViewType(I)I
            move-result p1
            """.trimIndent(),
        )
    }
}

private fun MutableMethod.patchFreeCustomizeBindDeleteRow(ext: String) {
    val instructions = implementation!!.instructions
    val stringIndex = instructions.indexOfFirst { insn ->
        insn.opcode == Opcode.CHECK_CAST &&
            insn is ReferenceInstruction &&
            insn.reference.toString().contains("Ljava/lang/String;")
    }
    check(stringIndex >= 0) { "delete row string check-cast not found in rbd\$a.onBindViewHolder" }
  val afterString = stringIndex + 1
    addInstructionsWithLabels(
        afterString,
        """
            const-string v3, "${Constants.DELETE_SHORTCUT_KEY}"
            invoke-virtual {v5, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v3
            if-eqz v3, :morphe_free_bind_continue
            invoke-static {p0, p1}, $ext->bindDeleteRowChecked(Ljava/lang/Object;Ljava/lang/Object;)V
            return-void
        """.trimIndent(),
        ExternalLabel("morphe_free_bind_continue", getInstruction(afterString)),
    )
}

private fun MutableMethod.patchFreeCustomizeToggleDeleteRow(ext: String) {
    val instructions = implementation!!.instructions
    val stringIndex = instructions.indexOfFirst { insn ->
        insn.opcode == Opcode.CHECK_CAST &&
            insn is ReferenceInstruction &&
            insn.reference.toString().contains("Ljava/lang/String;")
    }
    check(stringIndex >= 0) { "delete toggle string check-cast not found in qbd.onCheckedChanged" }
    val afterString = stringIndex + 1
    val epilogueIndex =
        instructions.indexOfFirst { insn ->
            insn.opcode == Opcode.CONST_STRING &&
                insn is ReferenceInstruction &&
                insn.reference.toString().contains("shortcuts_flag.3")
        }
    check(epilogueIndex > afterString) { "shortcuts_flag.3 epilogue not found in qbd.onCheckedChanged" }

    addInstructionsWithLabels(
        afterString,
        """
            const-string v6, "${Constants.DELETE_SHORTCUT_KEY}"
            invoke-virtual {v3, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v6
            if-eqz v6, :morphe_free_toggle_continue
            invoke-static {v2, p2}, $ext->toggleDeleteShortcut(Ljava/lang/Object;Z)V
            goto/16 :morphe_free_toggle_epilogue
        """.trimIndent(),
        ExternalLabel("morphe_free_toggle_continue", getInstruction(afterString)),
        ExternalLabel("morphe_free_toggle_epilogue", getInstruction(epilogueIndex)),
    )
}

private fun MutableMethod.indexOfLastReturnVoid(): Int =
    implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }