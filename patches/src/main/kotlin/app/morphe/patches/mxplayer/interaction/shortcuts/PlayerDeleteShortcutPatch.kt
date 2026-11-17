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
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.mxplayer.misc.extension.sharedExtensionPatch
import app.morphe.patches.mxplayer.misc.fix.mxPlayerPatchedInstallPatch
import app.morphe.patches.mxplayer.shared.Constants
import app.morphe.patches.mxplayer.shared.Constants.ACTIVITY_SCREEN_CLASS
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_PRO
import app.morphe.patches.mxplayer.shared.Constants.DELETE_DIALOG_INNER_CLASS
import app.morphe.patches.mxplayer.shared.Constants.DELETE_PP0_FIELD_NAME
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_BIT
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_KEY
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_LIST_INDEX
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_VIEW_TYPE
import app.morphe.patches.mxplayer.shared.Constants.SHORTCUT_ADAPTER_ON_CLICK_CLASS
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * Adds "Delete" to Customize Items and wires it to [ActivityScreen.w] (stock delete + confirm dialog).
 * Fingerprints and shortcuts_flag.3 handling verified on MX Player Pro 1.93.4 (2001002584).
 */
internal val playerDeleteShortcutResourcePatch = resourcePatch(
    name = null,
    description = "Adds delete to list_shortcuts and delete_file to list_shortcuts_eng.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)

    execute {
        // Pro 1.93.4 ships arrays only in resources.arsc (no decoded values/arrays.xml in the APK).
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
    description = "Adds a Delete item to the player's Customize Items list. Tapping it runs the same " +
        "delete flow as Menu → Delete (confirmation dialog, then next file or exit). " +
        "Targets MX Player Pro ${Constants.TARGET_VERSION} (versionCode ${Constants.TARGET_VERSION_CODE}).",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)
    dependsOn(sharedExtensionPatch, mxPlayerPatchedInstallPatch, playerDeleteShortcutResourcePatch)

    execute {
        val bitHex = "0x%x".format(DELETE_SHORTCUT_BIT)

        val activityScreenClass = mutableClassDefBy(ACTIVITY_SCREEN_CLASS)
        activityScreenClass.fields.add(
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

        val deleteInvoker = ImmutableMethod(
            ACTIVITY_SCREEN_CLASS,
            "patch_deletePlayingFile",
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.SYNTHETIC.value,
            null,
            null,
            MutableMethodImplementation(2),
        ).toMutable()
        deleteInvoker.addInstructions(
            0,
            """
                new-instance v0, ${DELETE_DIALOG_INNER_CLASS}
                invoke-direct {v0, p0}, ${DELETE_DIALOG_INNER_CLASS}-><init>(${ACTIVITY_SCREEN_CLASS})V
                return-void
            """.trimIndent(),
        )
        activityScreenClass.methods.add(deleteInvoker)

        val remapShortcutViewType = ImmutableMethod(
            ACTIVITY_SCREEN_CLASS,
            "patch_remapShortcutViewType",
            listOf(ImmutableMethodParameter("I", null, "viewType")),
            "I",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value,
            null,
            null,
            MutableMethodImplementation(2),
        ).toMutable()
        remapShortcutViewType.addInstructions(
            0,
            """
                const/16 v0, $DELETE_SHORTCUT_VIEW_TYPE
                if-ne p0, v0, :morphe_remap_ret
                const/4 p0, 0x0
                :morphe_remap_ret
                return p0
            """.trimIndent(),
        )
        activityScreenClass.methods.add(remapShortcutViewType)

        val persistShortcutFlags = ImmutableMethod(
            ACTIVITY_SCREEN_CLASS,
            "patch_persistShortcutFlags",
            listOf(ImmutableMethodParameter("Lo20\$a;", null, "adapter")),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value,
            null,
            null,
            MutableMethodImplementation(4),
        ).toMutable()
        persistShortcutFlags.addInstructions(
            0,
            """
                iget v0, p0, Lo20${'$'}a;->d:I
                sget-object v1, LlV;->A:LM90;
                iget-object v1, v1, LM90;->d:Landroid/content/SharedPreferences;
                invoke-interface {v1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
                move-result-object v1
                const-string v2, "shortcuts_flag.3"
                invoke-interface {v1, v2, v0}, Landroid/content/SharedPreferences${'$'}Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences${'$'}Editor;
                invoke-interface {v1}, Landroid/content/SharedPreferences${'$'}Editor;->commit()Z
                return-void
            """.trimIndent(),
        )
        activityScreenClass.methods.add(persistShortcutFlags)

        val bindDeleteCustomizeItem = ImmutableMethod(
            ACTIVITY_SCREEN_CLASS,
            "patch_bindDeleteShortcutCustomizeItem",
            listOf(
                ImmutableMethodParameter("Lo20\$a;", null, "adapter"),
                ImmutableMethodParameter("Lo20\$a\$a;", null, "holder"),
                ImmutableMethodParameter("I", null, "position"),
            ),
            "Z",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value,
            null,
            null,
            MutableMethodImplementation(6),
        ).toMutable()
        bindDeleteCustomizeItem.addInstructions(
            0,
            """
                const/16 v0, $DELETE_SHORTCUT_LIST_INDEX
                if-eq p2, v0, :morphe_bind_not_delete
                iget-object v0, p1, Lo20${'$'}a${'$'}a;->I:Landroidx/appcompat/widget/AppCompatCheckBox;
                invoke-virtual {v0}, Landroid/view/View;->getContext()Landroid/content/Context;
                move-result-object v1
                const v2, 0x7f120475
                invoke-virtual {v1, v2}, Landroid/content/Context;->getString(I)Ljava/lang/String;
                move-result-object v1
                invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                iget v1, p0, Lo20${'$'}a;->d:I
                const v2, $bitHex
                and-int/2addr v1, v2
                if-eqz v1, :morphe_bind_off
                const/4 v1, 0x1
                goto :morphe_bind_set
                :morphe_bind_off
                const/4 v1, 0x0
                :morphe_bind_set
                invoke-virtual {v0, v1}, Landroid/widget/CompoundButton;->setChecked(Z)V
                new-instance v1, Ln20;
                invoke-direct {v1, p0, p2, p1}, Ln20;-><init>(Lo20${'$'}a;ILo20${'$'}a${'$'}a;)V
                invoke-virtual {v0, v1}, Landroid/widget/CompoundButton;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
                const/4 v0, 0x1
                return v0
                :morphe_bind_not_delete
                const/4 v0, 0x0
                return v0
            """.trimIndent(),
        )
        activityScreenClass.methods.add(bindDeleteCustomizeItem)

        val toggleDeleteCustomizeItem = ImmutableMethod(
            ACTIVITY_SCREEN_CLASS,
            "patch_onDeleteShortcutCustomizeToggle",
            listOf(
                ImmutableMethodParameter("Ln20;", null, "listener"),
                ImmutableMethodParameter("Landroid/widget/CompoundButton;", null, "button"),
            ),
            "Z",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value,
            null,
            null,
            MutableMethodImplementation(4),
        ).toMutable()
        toggleDeleteCustomizeItem.addInstructions(
            0,
            """
                iget v0, p0, Ln20;->d:I
                const/16 v1, $DELETE_SHORTCUT_LIST_INDEX
                if-eq v0, v1, :morphe_toggle_not_delete
                invoke-virtual {p1}, Landroid/widget/CompoundButton;->isChecked()Z
                move-result v0
                iget-object v1, p0, Ln20;->k:Lo20${'$'}a;
                if-eqz v0, :morphe_toggle_on
                const v0, $bitHex
                invoke-static {v1, v0}, Lo20${'$'}a;->r(Lo20${'$'}a;I)V
                goto :morphe_toggle_persist
                :morphe_toggle_on
                const v0, $bitHex
                invoke-static {v1, v0}, Lo20${'$'}a;->s(Lo20${'$'}a;I)V
                :morphe_toggle_persist
                invoke-static {v1}, $ACTIVITY_SCREEN_CLASS->patch_persistShortcutFlags(Lo20${'$'}a;)V
                const/4 v0, 0x1
                return v0
                :morphe_toggle_not_delete
                const/4 v0, 0x0
                return v0
            """.trimIndent(),
        )
        activityScreenClass.methods.add(toggleDeleteCustomizeItem)

        CustomizeItemsOnBindFingerprint.method.cloneParameters().apply {
            val continueInstruction = getInstruction(0)
            addInstructionsWithLabels(
                0,
                """
                invoke-static {p0, p1, p2}, $ACTIVITY_SCREEN_CLASS->patch_bindDeleteShortcutCustomizeItem(Lo20${'$'}a;Lo20${'$'}a${'$'}a;I)Z
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
                invoke-static {p0, p1}, $ACTIVITY_SCREEN_CLASS->patch_onDeleteShortcutCustomizeToggle(Ln20;Landroid/widget/CompoundButton;)Z
                move-result v0
                if-eqz v0, :morphe_toggle_continue
                return-void
                """.trimIndent(),
                ExternalLabel("morphe_toggle_continue", continueInstruction),
            )
        }

        val syncDeleteShortcut = ImmutableMethod(
            ACTIVITY_SCREEN_CLASS,
            "patch_syncDeleteShortcut",
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.SYNTHETIC.value,
            null,
            null,
            MutableMethodImplementation(12),
        ).toMutable()
        syncDeleteShortcut.addInstructions(
            0,
            """
                sget v6, LV90;->e1:I
                const v7, $bitHex
                and-int/2addr v7, v6
                if-nez v7, :morphe_delete_shortcut_hide
                iget-object v7, p0, $ACTIVITY_SCREEN_CLASS->$DELETE_PP0_FIELD_NAME:LPp0;
                if-nez v7, :morphe_delete_shortcut_add_existing
                new-instance v0, LPp0;
                const/16 v5, $DELETE_SHORTCUT_VIEW_TYPE
                const/4 v6, 0x0
                const/4 v7, 0x0
                const/4 v8, 0x0
                const v9, 0x7f080685
                const v2, 0x7f120475
                invoke-virtual {p0, v2}, Landroid/content/Context;->getString(I)Ljava/lang/String;
                move-result-object v10
                move-object v4, v0
                invoke-direct/range { v4 .. v10 }, LPp0;-><init>(IIZZILjava/lang/String;)V
                iput-object v0, p0, $ACTIVITY_SCREEN_CLASS->$DELETE_PP0_FIELD_NAME:LPp0;
                :morphe_delete_shortcut_add_existing
                iget-object v6, p0, ${ACTIVITY_SCREEN_CLASS}->A0:Ljava/util/ArrayList;
                iget-object v7, p0, $ACTIVITY_SCREEN_CLASS->$DELETE_PP0_FIELD_NAME:LPp0;
                invoke-virtual {v6, v7}, Ljava/util/ArrayList;->contains(Ljava/lang/Object;)Z
                move-result v8
                if-nez v8, :morphe_delete_shortcut_done
                invoke-virtual {v6, v7}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                goto :morphe_delete_shortcut_done
                :morphe_delete_shortcut_hide
                iget-object v7, p0, $ACTIVITY_SCREEN_CLASS->$DELETE_PP0_FIELD_NAME:LPp0;
                if-eqz v7, :morphe_delete_shortcut_done
                iget-object v6, p0, ${ACTIVITY_SCREEN_CLASS}->A0:Ljava/util/ArrayList;
                invoke-virtual {v6, v7}, Ljava/util/ArrayList;->remove(Ljava/lang/Object;)Z
                const/4 v7, 0x0
                iput-object v7, p0, $ACTIVITY_SCREEN_CLASS->$DELETE_PP0_FIELD_NAME:LPp0;
                :morphe_delete_shortcut_done
                return-void
            """.trimIndent(),
        )
        activityScreenClass.methods.add(syncDeleteShortcut)

        ActivityScreenBuildShortcutsFingerprint.method.cloneParameters().apply {
            val returnIndex = indexOfLastReturnVoid()
            addInstructions(
                returnIndex,
                """
                invoke-virtual {p0}, $ACTIVITY_SCREEN_CLASS->patch_syncDeleteShortcut()V
                """.trimIndent(),
            )
        }

        ShortcutAdapterOnClickFingerprint.method.cloneParameters().apply {
            addInstructions(
                0,
                """
                const/16 v0, $DELETE_SHORTCUT_VIEW_TYPE
                iget-object v1, p0, ${SHORTCUT_ADAPTER_ON_CLICK_CLASS}->d:LSp0${'$'}g;
                iget-object v2, v1, LSp0${'$'}g;->I:LPp0;
                if-eqz v2, :morphe_onclick_continue
                iget v3, v2, LPp0;->b:I
                if-ne v3, v0, :morphe_onclick_continue
                iget-object v0, v1, LSp0${'$'}g;->N:LSp0;
                iget-object v0, v0, LSp0;->d:Lcom/mxtech/videoplayer/ActivityScreen;
                invoke-virtual {v0}, ${ACTIVITY_SCREEN_CLASS}->patch_deletePlayingFile()V
                return-void
                :morphe_onclick_continue
                """.trimIndent(),
            )
        }

        ShortcutAdapterCreateViewHolderFingerprint.method.cloneParameters().apply {
            addInstructions(
                0,
                """
                invoke-static {p1}, $ACTIVITY_SCREEN_CLASS->patch_remapShortcutViewType(I)I
                move-result p1
                """.trimIndent(),
            )
        }
    }
}

private fun MutableMethod.indexOfFirstPackedSwitch(): Int =
    implementation!!.instructions.indexOfFirst { it.opcode == Opcode.PACKED_SWITCH }.also { index ->
        require(index >= 0) { "packed-switch not found in ${definingClass}->$name" }
    }

private fun MutableMethod.indexOfLastReturnVoid(): Int =
    implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
