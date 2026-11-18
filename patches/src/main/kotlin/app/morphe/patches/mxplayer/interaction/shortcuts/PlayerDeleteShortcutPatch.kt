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
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableField

/**
 * Delete shortcut for MX Player Pro.
 *
 * Technique credit: [FTL-Patches](https://github.com/BlazeFTL/FTL-Patches) (BlazeFTL) —
 * PlayerDefaultShortcutsPatch, CleanPlayerSidebar* / ModSettings invoke-static hooks,
 * and resource-only sidebar edits. Logic lives in the mxplayerpro extension; patches only
 * add resources, one ActivityScreen field, and thin invoke-static bridges.
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

        CustomizeItemsOnBindFingerprint.method.cloneParameters().apply {
            val continueInstruction = getInstruction(0)
            addInstructionsWithLabels(
                0,
                """
                invoke-static {p0, p1, p2}, $ext->bindCustomizeItem(Ljava/lang/Object;Ljava/lang/Object;I)Z
                move-result v0
                if-eqz v0, :morphe_bind_continue
                return-void
                """.trimIndent(),
                ExternalLabel("morphe_bind_continue", continueInstruction),
            )
        }

        CustomizeItemsToggleFingerprint.method.cloneParameters().apply {
            val continueInstruction = getInstruction(0)
            addInstructionsWithLabels(
                0,
                """
                invoke-static {p0, p1}, $ext->onCustomizeToggle(Ljava/lang/Object;Landroid/widget/CompoundButton;)Z
                move-result v0
                if-eqz v0, :morphe_toggle_continue
                return-void
                """.trimIndent(),
                ExternalLabel("morphe_toggle_continue", continueInstruction),
            )
        }

        MenuMoreShortcutsPanelFingerprint.method.cloneParameters().apply {
            val returnIndex = indexOfLastReturnVoid()
            addInstructions(
                returnIndex,
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

private fun MutableMethod.indexOfLastReturnVoid(): Int =
    implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }