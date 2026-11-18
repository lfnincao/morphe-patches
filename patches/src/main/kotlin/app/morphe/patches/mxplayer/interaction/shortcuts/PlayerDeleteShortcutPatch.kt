package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.mxplayer.misc.extension.sharedExtensionPatch
import app.morphe.patches.mxplayer.misc.fix.mxPlayerPatchedInstallPatch
import app.morphe.patches.mxplayer.shared.Constants
import app.morphe.patches.mxplayer.shared.Constants.ACTIVITY_SCREEN_CLASS
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_PRO
import app.morphe.patches.mxplayer.shared.Constants.DELETE_PP0_FIELD_NAME
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_BIT
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_EXTENSION
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_KEY
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_LIST_INDEX
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableField

/**
 * Delete shortcut for MX Player Pro.
 *
 * Technique credit: [FTL-Patches](https://github.com/BlazeFTL/FTL-Patches) (BlazeFTL) —
 * resource `list_shortcuts*` edits, default shortcut bitmask migration, and thin extension hooks.
 * Customize rows use stock `Lo20$a.l` label binding; we only branch on index 12 before packed-switch.
 */
internal val playerDeleteShortcutResourcePatch = resourcePatch(
    name = null,
    description = "Adds delete to list_shortcuts and delete_file to list_shortcuts_eng.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)

    execute {
        resourceXml("res/values/arrays.xml").use { arrays ->
            arrays.resource("list_shortcuts_eng").appendChild("item") {
                text = DELETE_SHORTCUT_KEY
            }
            arrays.resource("list_shortcuts").appendChild("item") {
                text = "@string/menu_delete"
            }
        }
    }
}

@Suppress("unused")
val playerDeleteShortcutPatch = bytecodePatch(
    name = "Player delete shortcut",
    description = "Adds Delete to Customize items and wires it to stock ActivityScreen delete flow.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)
    dependsOn(
        sharedExtensionPatch,
        mxPlayerPatchedInstallPatch,
        playerDeleteShortcutResourcePatch,
        playerDefaultShortcutsPatch,
    )

    execute {
        val ext = DELETE_SHORTCUT_EXTENSION

        mutableClassDefBy(ACTIVITY_SCREEN_CLASS).fields.add(
            ImmutableField(
                ACTIVITY_SCREEN_CLASS,
                DELETE_PP0_FIELD_NAME,
                "LPp0;",
                AccessFlags.PUBLIC.value,
                null,
                null,
                null,
            ).toMutable(),
        )

        CustomizeItemsOnBindFingerprint.method.cloneParameters().patchCustomizeBindRow12(ext)

        CustomizeItemsToggleFingerprint.method.cloneParameters().patchCustomizeToggleRow12()

        MenuMoreShortcutsPanelFingerprint.method.cloneParameters().apply {
            addInstructions(
                indexOfLastReturnVoid(),
                """
                invoke-static {p0}, $ext->ensureShortcutsPanelVisible(Ljava/lang/Object;)V
                """.trimIndent(),
            )
        }

        ActivityScreenBuildShortcutsFingerprint.method.cloneParameters().apply {
            addInstructions(
                indexOfLastReturnVoid(),
                """
                invoke-static {p0}, $ext->syncToolbarShortcut(Ljava/lang/Object;)V
                """.trimIndent(),
            )
        }

        ShortcutAdapterOnClickFingerprint.method.cloneParameters().apply {
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

        ShortcutAdapterCreateViewHolderFingerprint.method.cloneParameters().apply {
            addInstructions(
                0,
                """
                invoke-static {p1}, $ext->remapShortcutViewType(I)I
                move-result p1
                """.trimIndent(),
            )
        }
    }
}

private fun MutableMethod.patchCustomizeBindRow12(ext: String) {
    val instructions = implementation!!.instructions
    val packedSwitchIndex = instructions.indexOfFirst { it.opcode == Opcode.PACKED_SWITCH }
    check(packedSwitchIndex >= 0) { "packed-switch not found in Lo20\$a.l" }

    addInstructionsWithLabels(
        packedSwitchIndex,
        """
            const/16 v3, $DELETE_SHORTCUT_LIST_INDEX
            if-ne v6, v3, :morphe_bind_pswitch
            invoke-static {v4, v0}, $ext->bindDeleteRowChecked(Ljava/lang/Object;Ljava/lang/Object;)V
            return-void
        """.trimIndent(),
        ExternalLabel("morphe_bind_pswitch", getInstruction(packedSwitchIndex)),
    )
}

private fun MutableMethod.patchCustomizeToggleRow12() {
    val instructions = implementation!!.instructions
    val packedSwitchIndex = instructions.indexOfFirst { it.opcode == Opcode.PACKED_SWITCH }
    check(packedSwitchIndex >= 0) { "packed-switch not found in n20.onCheckedChanged" }
    val epilogueIndex =
        instructions.indexOfFirst { insn ->
            insn.opcode == Opcode.SGET_OBJECT &&
                insn is ReferenceInstruction &&
                insn.reference.toString().contains("LlV;") &&
                insn.reference.toString().contains("->A:")
        }
    check(epilogueIndex > packedSwitchIndex) {
        "LlV.A sget epilogue (same as Loop goto target) not found in n20.onCheckedChanged"
    }

    addInstructionsWithLabels(
        packedSwitchIndex,
        """
            const/16 v4, $DELETE_SHORTCUT_LIST_INDEX
            if-ne v3, v4, :morphe_toggle_pswitch
            iget-object v2, v2, Lo20${'$'}a${'$'}a;->I:Landroidx/appcompat/widget/AppCompatCheckBox;
            invoke-virtual {v2}, Landroid/widget/CompoundButton;->isChecked()Z
            move-result v2
            if-eqz v2, :morphe_delete_off
            const/16 v2, ${Constants.DELETE_SHORTCUT_BIT}
            invoke-static {v0, v2}, Lo20${'$'}a;->s(Lo20${'$'}a;I)V
            goto :morphe_toggle_epilogue
            :morphe_delete_off
            const/16 v2, ${Constants.DELETE_SHORTCUT_CLEAR_MASK}
            invoke-static {v0, v2}, Lo20${'$'}a;->r(Lo20${'$'}a;I)V
            goto :morphe_toggle_epilogue
        """.trimIndent(),
        ExternalLabel("morphe_toggle_pswitch", getInstruction(packedSwitchIndex)),
        ExternalLabel("morphe_toggle_epilogue", getInstruction(epilogueIndex)),
    )
}

private fun MutableMethod.indexOfLastReturnVoid(): Int =
    implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }