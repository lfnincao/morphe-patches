package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.mxplayer.shared.Constants
import app.morphe.patches.mxplayer.shared.Constants.ACTIVITY_SCREEN_CLASS
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_BIT_FREE
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_EXTENSION_FREE
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
    FreeActivityScreenSortShortcutsFingerprint.method.cloneParameters().patchRebuildDeleteShortcutBeforeSort(ext)
    FreeShortcutAdapterOnClickFingerprint.method.cloneParameters().apply {
        val continueInstruction = getInstruction(0)
        addInstructionsWithLabels(
            0,
            """
            invoke-static/range {p0 .. p0}, $ext->onToolbarShortcutClick(Ljava/lang/Object;)Z
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
            invoke-static {p2}, $ext->remapShortcutViewType(I)I
            move-result p2
            """.trimIndent(),
        )
    }
}

private fun MutableMethod.patchFreeCustomizeBindDeleteRow(ext: String) {
    val instructions = implementation!!.instructions
    val stringCastIndex = instructions.indexOfFirst { insn ->
        insn.opcode == Opcode.CHECK_CAST &&
            insn is ReferenceInstruction &&
            insn.reference.toString().contains("Ljava/lang/String;")
    }
    check(stringCastIndex >= 0) { "String check-cast not found in rbd\$a.onBindViewHolder" }
    val afterString = stringCastIndex + 1
    val continueInstruction = getInstruction(afterString)
    val listenerIndex = instructions.indexOfFirst { insn ->
        insn.opcode == Opcode.NEW_INSTANCE &&
            insn is ReferenceInstruction &&
            insn.reference.toString().contains("Lqbd;")
    }
    check(listenerIndex > afterString) { "Lqbd listener tail not found in rbd\$a.onBindViewHolder" }
    val listenerInsn = getInstruction(listenerIndex)

    addInstructionsWithLabels(
        afterString,
        """
            const-string v6, "${Constants.DELETE_SHORTCUT_KEY}"
            invoke-virtual {v5, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v6
            if-eqz v6, :morphe_free_bind_delete_continue
            check-cast p1, Lrbd${'$'}a${'$'}a;
            iget-object v0, p1, Lrbd${'$'}a${'$'}a;->b:Landroidx/appcompat/widget/AppCompatCheckBox;
            iget-object v1, p0, Lrbd${'$'}a;->k:Landroid/content/Context;
            sget v2, Lcah;->delete_file_item:I
            invoke-virtual {v1, v2}, Landroid/content/Context;->getString(I)Ljava/lang/String;
            move-result-object v1
            invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
            iget v1, p0, Lrbd${'$'}a;->j:I
            const v2, $DELETE_SHORTCUT_BIT_FREE
            and-int/2addr v1, v2
            if-eqz v1, :morphe_free_bind_delete_off
            const/4 v1, 0x1
            goto :morphe_free_bind_delete_set
            :morphe_free_bind_delete_off
            const/4 v1, 0x0
            :morphe_free_bind_delete_set
            invoke-virtual {v0, v1}, Landroid/widget/CompoundButton;->setChecked(Z)V
            goto/16 :morphe_free_bind_listener
        """.trimIndent(),
        ExternalLabel("morphe_free_bind_delete_continue", continueInstruction),
        ExternalLabel("morphe_free_bind_listener", listenerInsn),
    )

    val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
    addInstructions(
        returnIndex,
        """
        invoke-static {p0, p1, p2}, $ext->overlayDeleteRowChecked(Ljava/lang/Object;Ljava/lang/Object;I)V
        """.trimIndent(),
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
    val epilogueIndex = instructions.indexOfFirst { insn ->
        insn.opcode == Opcode.SGET_OBJECT &&
            insn is ReferenceInstruction &&
            insn.reference.toString().contains("Lzjc;") &&
            insn.reference.toString().contains("->o:")
    }
    check(epilogueIndex > afterString) { "Lzjc.o prefs epilogue not found in qbd.onCheckedChanged" }

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

private fun MutableMethod.patchRebuildDeleteShortcutBeforeSort(ext: String) {
    val sortIndex = implementation!!.instructions.indexOfFirst { insn ->
        insn is ReferenceInstruction &&
            insn.reference.toString().contains("Ljava/util/Collections;->sort")
    }
    check(sortIndex >= 0) { "Collections.sort not found in ActivityScreen.Ya" }
    addInstructions(
        sortIndex,
        """
        invoke-static {p0}, $ext->rebuildDeleteShortcutInYa(Ljava/lang/Object;)V
        """.trimIndent(),
    )
}

private fun MutableMethod.indexOfLastReturnVoid(): Int =
    implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }