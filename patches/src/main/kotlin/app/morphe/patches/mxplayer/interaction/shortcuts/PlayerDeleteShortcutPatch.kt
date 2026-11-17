package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.mxplayer.misc.extension.sharedExtensionPatch
import app.morphe.patches.mxplayer.shared.Constants
import app.morphe.patches.mxplayer.shared.Constants.ACTIVITY_SCREEN_CLASS
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_PRO
import app.morphe.patches.mxplayer.shared.Constants.DELETE_DIALOG_INNER_CLASS
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_BIT
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_KEY
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_VIEW_TYPE
import app.morphe.patches.mxplayer.shared.Constants.DELETE_WUH_FIELD_NAME
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

/**
 * Adds "Delete" to Customize Items and wires it to [ActivityScreen.w] (stock delete + confirm dialog).
 * Fingerprint and shortcuts_flag patterns follow BlazeFTL/FTL-Patches MX Player patches.
 */
internal val playerDeleteShortcutResourcePatch = resourcePatch(
    name = null,
    description = "Adds delete_file to list_shortcuts_eng.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)

    execute {
        document("res/values/arrays.xml").use { document ->
            val arrays = document.getElementsByTagName("string-array")
            for (i in 0 until arrays.length) {
                val node = arrays.item(i)
                if (node.attributes.getNamedItem("name")?.nodeValue == "list_shortcuts_eng") {
                    val item = document.createElement("item")
                    item.textContent = DELETE_SHORTCUT_KEY
                    node.appendChild(item)
                    return@use
                }
            }
            error("list_shortcuts_eng not found")
        }
    }
}

@Suppress("unused")
val playerDeleteShortcutPatch = bytecodePatch(
    name = "Player delete shortcut",
    description = "Adds a Delete item to the player's Customize Items list. Tapping it runs the same " +
        "delete flow as Menu → Delete (confirmation dialog, then next file or exit). " +
        "Targets MX Player Pro ${Constants.TARGET_VERSION} (player code verified on " +
        "com.mxtech.videoplayer.ad ${Constants.TARGET_VERSION}).",
    default = true,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_PRO)
    dependsOn(sharedExtensionPatch, playerDeleteShortcutResourcePatch)

    execute {
        val bitHex = "0x%x".format(DELETE_SHORTCUT_BIT)

        fun appendKeyToShortcutList(init: IgCustomizeItemsAdapterInitFingerprint) {
            val method = init.method
            val index = method.implementation!!.instructions.size - 1
            method.addInstructions(
                index,
                """
                    iget-object v0, p0, ${method.definingClass}->i:Ljava/util/ArrayList;
                    const-string v1, "$DELETE_SHORTCUT_KEY"
                    invoke-virtual {v0, v1}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                """.trimIndent(),
            )
        }

        appendKeyToShortcutList(IgCustomizeItemsAdapterInitFingerprint)
        TunerCustomizeItemsAdapterInitFingerprint.method.let { method ->
            val index = method.implementation!!.instructions.size - 1
            method.addInstructions(
                index,
                """
                    iget-object v0, p0, ${method.definingClass}->i:Ljava/util/ArrayList;
                    const-string v1, "$DELETE_SHORTCUT_KEY"
                    invoke-virtual {v0, v1}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                """.trimIndent(),
            )
        }

        IgCustomizeItemsToggleFingerprint.method.cloneParameters(8).addInstructions(
            0,
            """
                iget-object v5, p0, Lhgc;->d:Ligc${'$'}a;
                iget-object v6, v5, Ligc${'$'}a;->i:Ljava/util/ArrayList;
                iget v7, p0, Lhgc;->b:I
                invoke-virtual {v6, v7}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;
                move-result-object v6
                check-cast v6, Ljava/lang/String;
                const-string v7, "$DELETE_SHORTCUT_KEY"
                invoke-virtual {v6, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v6
                if-eqz v6, :morphe_igc_toggle_continue
                invoke-virtual {p1}, Landroid/widget/CompoundButton;->isChecked()Z
                move-result v6
                if-eqz v6, :morphe_igc_toggle_on
                iget v6, v5, Ligc${'$'}a;->j:I
                const v7, $bitHex
                not-int v7, v7
                and-int/2addr v6, v7
                iput v6, v5, Ligc${'$'}a;->j:I
                goto :morphe_igc_toggle_store
                :morphe_igc_toggle_on
                iget v6, v5, Ligc${'$'}a;->j:I
                const v7, $bitHex
                or-int/2addr v6, v7
                iput v6, v5, Ligc${'$'}a;->j:I
                :morphe_igc_toggle_store
                sget-object v6, Lwpb;->o:Lt1e;
                iget-object v6, v6, Lt1e;->b:Landroid/content/SharedPreferences;
                invoke-interface {v6}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
                move-result-object v6
                iget v7, v5, Ligc${'$'}a;->j:I
                const-string v5, "shortcuts_flag.3"
                invoke-interface {v6, v5, v7}, Landroid/content/SharedPreferences${'$'}Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences${'$'}Editor;
                move-result-object v5
                invoke-interface {v5}, Landroid/content/SharedPreferences${'$'}Editor;->commit()Z
                return-void
                :morphe_igc_toggle_continue
            """.trimIndent(),
        )

        TunerCustomizeItemsToggleFingerprint.method.cloneParameters(8).addInstructions(
            0,
            """
                iget-object v5, p0, Lcom/mxtech/videoplayer/preference/j;->d:Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;
                iget-object v6, v5, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;->i:Ljava/util/ArrayList;
                iget v7, p0, Lcom/mxtech/videoplayer/preference/j;->b:I
                invoke-virtual {v6, v7}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;
                move-result-object v6
                check-cast v6, Ljava/lang/String;
                const-string v7, "$DELETE_SHORTCUT_KEY"
                invoke-virtual {v6, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v6
                if-eqz v6, :morphe_tuner_toggle_continue
                invoke-virtual {p1}, Landroid/widget/CompoundButton;->isChecked()Z
                move-result v6
                if-eqz v6, :morphe_tuner_toggle_set
                iget v6, v5, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;->j:I
                const v7, $bitHex
                not-int v7, v7
                and-int/2addr v6, v7
                iput v6, v5, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;->j:I
                goto :morphe_tuner_toggle_dirty
                :morphe_tuner_toggle_set
                iget v6, v5, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;->j:I
                const v7, $bitHex
                or-int/2addr v6, v7
                iput v6, v5, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;->j:I
                :morphe_tuner_toggle_dirty
                iget-object v5, v5, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;->l:Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a;
                const/4 v6, 0x1
                iput-boolean v6, v5, Ll0k;->b:Z
                return-void
                :morphe_tuner_toggle_continue
            """.trimIndent(),
        )

        IgCustomizeItemsOnBindFingerprint.method.cloneParameters(10).addInstructions(
            0,
            """
                check-cast p1, Ligc${'$'}a${'$'}a;
                iget-object v0, p0, Ligc${'$'}a;->i:Ljava/util/ArrayList;
                invoke-virtual {v0, p2}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Ljava/lang/String;
                const-string v1, "$DELETE_SHORTCUT_KEY"
                invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :morphe_igc_bind_continue
                iget-object v0, p1, Ligc${'$'}a${'$'}a;->b:Landroidx/appcompat/widget/AppCompatCheckBox;
                sget v1, Ln6g;->menu_delete:I
                iget-object v2, p0, Ligc${'$'}a;->k:Landroid/content/Context;
                invoke-virtual {v2, v1}, Landroid/content/Context;->getString(I)Ljava/lang/String;
                move-result-object v1
                invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                iget v1, p0, Ligc${'$'}a;->j:I
                const v2, $bitHex
                and-int/2addr v1, v2
                if-eqz v1, :morphe_igc_bind_off
                const/4 v1, 0x1
                goto :morphe_igc_bind_set
                :morphe_igc_bind_off
                const/4 v1, 0x0
                :morphe_igc_bind_set
                invoke-virtual {v0, v1}, Landroid/widget/CompoundButton;->setChecked(Z)V
                new-instance v1, Lhgc;
                invoke-direct {v1, p0, p2, p1}, Lhgc;-><init>(Ligc${'$'}a;ILigc${'$'}a${'$'}a;)V
                invoke-virtual {v0, v1}, Landroid/widget/CompoundButton;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
                return-void
                :morphe_igc_bind_continue
            """.trimIndent(),
        )

        TunerCustomizeItemsOnBindFingerprint.method.cloneParameters(10).addInstructions(
            0,
            """
                check-cast p1, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c${'$'}a;
                iget-object v0, p0, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;->i:Ljava/util/ArrayList;
                invoke-virtual {v0, p2}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Ljava/lang/String;
                const-string v1, "$DELETE_SHORTCUT_KEY"
                invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :morphe_tuner_bind_continue
                iget-object v0, p1, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c${'$'}a;->b:Landroidx/appcompat/widget/AppCompatCheckBox;
                sget v1, Ln6g;->menu_delete:I
                iget-object v2, p0, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;->k:Landroid/content/Context;
                invoke-virtual {v2, v1}, Landroid/content/Context;->getString(I)Ljava/lang/String;
                move-result-object v1
                invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                iget v1, p0, Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;->j:I
                const v2, $bitHex
                and-int/2addr v1, v2
                if-eqz v1, :morphe_tuner_bind_off
                const/4 v1, 0x1
                goto :morphe_tuner_bind_set
                :morphe_tuner_bind_off
                const/4 v1, 0x0
                :morphe_tuner_bind_set
                invoke-virtual {v0, v1}, Landroid/widget/CompoundButton;->setChecked(Z)V
                new-instance v1, Lcom/mxtech/videoplayer/preference/j;
                invoke-direct {v1, p0, p2, p1}, Lcom/mxtech/videoplayer/preference/j;-><init>(Lcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c;ILcom/mxtech/videoplayer/preference/TunerControl${'$'}a${'$'}c${'$'}a;)V
                invoke-virtual {v0, v1}, Landroid/widget/CompoundButton;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
                return-void
                :morphe_tuner_bind_continue
            """.trimIndent(),
        )

        val activityScreenClass = mutableClassDefBy(ACTIVITY_SCREEN_CLASS)
        activityScreenClass.fields.add(
            ImmutableField(
                ACTIVITY_SCREEN_CLASS,
                DELETE_WUH_FIELD_NAME,
                "Lwuh;",
                AccessFlags.PUBLIC.value,
                null,
                null,
                null,
            ),
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
                new-instance v0, $DELETE_DIALOG_INNER_CLASS
                invoke-direct {v0, p0}, $DELETE_DIALOG_INNER_CLASS;-><init>($ACTIVITY_SCREEN_CLASS)V
                return-void
            """.trimIndent(),
        )
        activityScreenClass.methods.add(deleteInvoker)

        val bb = ActivityScreenBuildShortcutsFingerprint.method
        bb.cloneParameters(12)
        val insertIndex = ActivityScreenBuildShortcutsFingerprint.instructionMatches.last().index + 1
        bb.addInstructions(
            insertIndex,
            """
                sget v6, Lh6e;->f1:I
                const v7, $bitHex
                and-int/2addr v7, v6
                if-nez v7, :morphe_delete_shortcut_hide
                iget-object v7, p0, $ACTIVITY_SCREEN_CLASS->$DELETE_WUH_FIELD_NAME:Lwuh;
                if-nez v7, :morphe_delete_shortcut_add_existing
                new-instance v0, Lwuh;
                const/16 v7, $DELETE_SHORTCUT_VIEW_TYPE
                const/4 v8, 0x0
                sget v9, Lc1g;->ic_more_delete_white:I
                sget v10, Ln6g;->menu_delete:I
                invoke-virtual {p0, v10}, Landroid/content/Context;->getString(I)Ljava/lang/String;
                move-result-object v10
                const/4 v11, 0x0
                const/4 v12, 0x0
                move-object v6, v0
                invoke-direct/range {v6 .. v12}, Lwuh;-><init>(IIILjava/lang/String;ZZ)V
                iput-object v0, p0, $ACTIVITY_SCREEN_CLASS->$DELETE_WUH_FIELD_NAME:Lwuh;
                :morphe_delete_shortcut_add_existing
                iget-object v6, p0, $ACTIVITY_SCREEN_CLASS;->b0:Ljava/util/ArrayList;
                iget-object v7, p0, $ACTIVITY_SCREEN_CLASS->$DELETE_WUH_FIELD_NAME:Lwuh;
                invoke-virtual {v6, v7}, Ljava/util/ArrayList;->contains(Ljava/lang/Object;)Z
                move-result v8
                if-nez v8, :morphe_delete_shortcut_done
                invoke-virtual {v6, v7}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                goto :morphe_delete_shortcut_done
                :morphe_delete_shortcut_hide
                iget-object v7, p0, $ACTIVITY_SCREEN_CLASS->$DELETE_WUH_FIELD_NAME:Lwuh;
                if-eqz v7, :morphe_delete_shortcut_done
                iget-object v6, p0, $ACTIVITY_SCREEN_CLASS;->b0:Ljava/util/ArrayList;
                invoke-virtual {v6, v7}, Ljava/util/ArrayList;->remove(Ljava/lang/Object;)Z
                const/4 v7, 0x0
                iput-object v7, p0, $ACTIVITY_SCREEN_CLASS->$DELETE_WUH_FIELD_NAME:Lwuh;
                :morphe_delete_shortcut_done
            """.trimIndent(),
        )

        ShortcutAdapterOnClickFingerprint.method.cloneParameters(4).addInstructions(
            0,
            """
                const/16 v0, $DELETE_SHORTCUT_VIEW_TYPE
                iget-object v1, p0, Lcom/mxtech/videoplayer/widget/b${'$'}g${'$'}a;->b:Lcom/mxtech/videoplayer/widget/b${'$'}g;
                iget-object v2, v1, Lcom/mxtech/videoplayer/widget/b${'$'}g;->b:Lwuh;
                if-eqz v2, :morphe_onclick_continue
                iget v3, v2, Lwuh;->b:I
                if-ne v3, v0, :morphe_onclick_continue
                iget-object v0, v1, Lcom/mxtech/videoplayer/widget/b${'$'}g;->g:Lcom/mxtech/videoplayer/widget/b;
                iget-object v0, v0, Lcom/mxtech/videoplayer/widget/b;->j:Lcom/mxtech/videoplayer/ActivityScreen;
                invoke-virtual {v0}, $ACTIVITY_SCREEN_CLASS;->patch_deletePlayingFile()V
                return-void
                :morphe_onclick_continue
            """.trimIndent(),
        )

        val createVh = ShortcutAdapterOnCreateViewHolderFingerprint.method
        createVh.cloneParameters(4)
        createVh.addInstructions(
            0,
            """
                const/16 v0, $DELETE_SHORTCUT_VIEW_TYPE
                if-ne p2, v0, :morphe_vh_continue
                const/16 p2, 0xe
                :morphe_vh_continue
            """.trimIndent(),
        )
    }
}