package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_BIT
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_VIEW_TYPE
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import java.io.File
import java.util.zip.ZipFile

/**
 * Static gates for delete-only toolbar clicks and bit-mask shortcut_flag.3 updates.
 */
internal fun verifyMxPlayerDeleteShortcutBehavior(apkFile: File): String {
    val dexFiles = readAllDex(apkFile)
    var lines = buildString {
        appendLine("=== delete shortcut behavior verify (${apkFile.name}) ===")

        val onClick =
            findMethod(dexFiles, "LSp0\$g\$a;", "onClick")
                ?: error("Sp0\$g\$a.onClick missing")
        val onClickCallsDelete =
            onClick.any {
                it is ReferenceInstruction &&
                    it.reference.toString().contains("onToolbarShortcutClick")
            }
        appendLine(
            if (onClickCallsDelete) "OK  Sp0\$g\$a.onClick delegates to onToolbarShortcutClick"
            else "MISSING  toolbar onClick hook",
        )

        val extClass =
            dexFiles.firstNotNullOfOrNull { dex ->
                dex.classes.firstOrNull {
                    it.type == "Lapp/morphe/extension/mxplayerpro/delete/DeleteShortcutPatch;"
                }
            } ?: error("DeleteShortcutPatch missing from APK")
        val clickMethod =
            extClass.methods.firstOrNull { it.name == "onToolbarShortcutClick" }
                ?: error("onToolbarShortcutClick missing")
        val clickInsns = clickMethod.implementation!!.instructions.toList()
        val guardsViewType =
            clickInsns.any { insn ->
                insn.opcode == Opcode.CONST_16 &&
                    (insn as NarrowLiteralInstruction).narrowLiteral == DELETE_SHORTCUT_VIEW_TYPE
            }
        appendLine(
            if (guardsViewType) "OK  onToolbarShortcutClick checks view type $DELETE_SHORTCUT_VIEW_TYPE"
            else "MISSING  onToolbarShortcutClick does not const view type $DELETE_SHORTCUT_VIEW_TYPE",
        )
        val guardsCachedPp0 =
            clickInsns.any { insn ->
                insn is ReferenceInstruction &&
                    insn.reference.toString().contains("getCachedPp0")
            }
        appendLine(
            if (guardsCachedPp0) "OK  onToolbarShortcutClick uses cached delete Pp0 identity"
            else "MISSING  onToolbarShortcutClick does not call getCachedPp0",
        )

        val sp0Create =
            findMethod(dexFiles, "LSp0;", "n")
                ?: error("Sp0.n missing")
        val remapsDeleteViewType =
            sp0Create.take(4).any {
                it is ReferenceInstruction &&
                    it.reference.toString().contains("remapShortcutViewType")
            }
        appendLine(
            if (remapsDeleteViewType) "OK  Sp0.n remaps delete view type for view holders"
            else "MISSING  Sp0.n has no remapShortcutViewType hook",
        )

        val toggle =
            findMethod(dexFiles, "Ln20;", "onCheckedChanged")
                ?: error("n20.onCheckedChanged missing")
        val toggleDeleteOnly =
            toggle.any {
                it is ReferenceInstruction &&
                    it.reference.toString().contains("onDeleteShortcutToggle")
            }
        appendLine(
            if (toggleDeleteOnly) "OK  n20.onCheckedChanged calls onDeleteShortcutToggle for delete row"
            else "MISSING  delete toggle hook",
        )

        val toggleHookIndex =
            toggle.indexOfFirst {
                it is ReferenceInstruction &&
                    it.reference.toString().contains("onDeleteShortcutToggle")
            }
        val returnAfterToggleHook =
            toggle.getOrNull(toggleHookIndex + 1)?.opcode == Opcode.RETURN_VOID
        appendLine(
            if (returnAfterToggleHook) {
                "OK  onDeleteShortcutToggle is followed by return-void (no stock epilogue reuse)"
            } else {
                "MISSING  onDeleteShortcutToggle must return-void before shortcuts_flag.3 putInt"
            },
        )
        val gotoAfterToggleHook =
            toggle.getOrNull(toggleHookIndex + 1).let { insn ->
                insn != null &&
                    (insn.opcode == Opcode.GOTO ||
                        insn.opcode == Opcode.GOTO_16 ||
                        insn.opcode == Opcode.GOTO_32)
            }
        appendLine(
            if (!gotoAfterToggleHook) "OK  delete toggle hook does not goto stock epilogue"
            else "MISSING  delete toggle hook must not goto shortcuts_flag.3 epilogue",
        )

        val toggleMethod =
            extClass.methods.firstOrNull { it.name == "onDeleteShortcutToggle" }
                ?: error("onDeleteShortcutToggle missing")
        val toggleCallsMerge =
            toggleMethod.implementation!!.instructions.any { insn ->
                insn is ReferenceInstruction &&
                    insn.reference.toString().contains("mergeShortcutsFlag3DeleteBit")
            }
        appendLine(
            if (toggleCallsMerge) "OK  onDeleteShortcutToggle merges shortcuts_flag.3 via mergeShortcutsFlag3DeleteBit"
            else "MISSING  toggle does not use bit-mask merge helper",
        )

        val mergeMethod =
            extClass.methods.firstOrNull { it.name == "mergeShortcutsFlag3DeleteBit" }
                ?: error("mergeShortcutsFlag3DeleteBit missing")
        val mergeUsesOrAnd =
            mergeMethod.implementation!!.instructions.any { insn ->
                (insn.opcode == Opcode.OR_INT_LIT16 || insn.opcode == Opcode.AND_INT_LIT16) &&
                    (insn as NarrowLiteralInstruction).narrowLiteral == DELETE_SHORTCUT_BIT
            }
        appendLine(
            if (mergeUsesOrAnd) "OK  mergeShortcutsFlag3DeleteBit uses or/and-lit16 on 0x2000"
            else "MISSING  merge helper does not or/and 0x2000 in dex",
        )
        val mergeCallsEffective =
            mergeMethod.implementation!!.instructions.any { insn ->
                insn is ReferenceInstruction &&
                    insn.reference.toString().contains("effectiveShortcutsFlag3")
            }
        appendLine(
            if (mergeCallsEffective) {
                "OK  mergeShortcutsFlag3DeleteBit reads effective flags via effectiveShortcutsFlag3 (V90.C())"
            } else {
                "MISSING  merge helper must call effectiveShortcutsFlag3 not raw getInt default 0"
            },
        )

        val bindMethod =
            extClass.methods.firstOrNull { it.name == "bindDeleteRowChecked" }
                ?: error("bindDeleteRowChecked missing")
        val bindUsesEffective =
            bindMethod.implementation!!.instructions.any { insn ->
                insn is ReferenceInstruction &&
                    insn.reference.toString().contains("effectiveShortcutsFlag3")
            }
        appendLine(
            if (bindUsesEffective) {
                "OK  bindDeleteRowChecked uses effectiveShortcutsFlag3 (V90.C())"
            } else {
                "MISSING  bindDeleteRowChecked must not use raw adapter field only"
            },
        )

        val normalizeAbsent =
            extClass.methods.none { it.name == "normalizeCustomizeShortcutFlags" }
        appendLine(
            if (normalizeAbsent) "OK  normalizeCustomizeShortcutFlags removed (stock V90.C() only)"
            else "MISSING  normalizeCustomizeShortcutFlags still present",
        )
    }
    val failed = lines.lines().any { it.startsWith("MISSING  ") }
    lines += if (failed) "FAILED\n" else "PASSED\n"
    require(lines.contains("PASSED")) { "Delete shortcut behavior gates failed:\n$lines" }
    return lines
}

private fun readAllDex(apk: File): List<DexBackedDexFile> =
    ZipFile(apk).use { zip ->
        zip.stream()
            .filter { !it.isDirectory && it.name.matches(Regex("classes\\d*\\.dex")) }
            .map { entry ->
                val bytes = zip.getInputStream(entry).readBytes()
                DexBackedDexFile.fromInputStream(Opcodes.getDefault(), bytes.inputStream())
            }
            .toList()
    }

private fun findMethod(
    dexFiles: List<DexBackedDexFile>,
    type: String,
    name: String,
): List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>? {
    for (dex in dexFiles) {
        val cls = dex.classes.firstOrNull { it.type == type } ?: continue
        val method = cls.methods.firstOrNull { it.name == name } ?: continue
        return method.implementation?.instructions?.toList()
    }
    return null
}