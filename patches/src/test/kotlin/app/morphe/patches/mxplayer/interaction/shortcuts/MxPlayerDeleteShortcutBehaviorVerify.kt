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
        val noExtensionToggle =
            toggle.none {
                it is ReferenceInstruction &&
                    it.reference.toString().contains("onDeleteShortcutToggle")
            }
        appendLine(
            if (noExtensionToggle) "OK  n20.onCheckedChanged persists delete via stock Lo20\$a.s/r"
            else "MISSING  onDeleteShortcutToggle still bypasses stock epilogue",
        )
        val toggleHasDeleteBit =
            toggle.any { insn ->
                insn.opcode == Opcode.CONST_16 &&
                    (insn as NarrowLiteralInstruction).narrowLiteral == DELETE_SHORTCUT_BIT
            }
        appendLine(
            if (toggleHasDeleteBit) "OK  delete toggle uses const 0x2000 for Lo20\$a.s"
            else "MISSING  delete toggle missing 0x2000 enable literal",
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
        val reflectionToggleAbsent =
            extClass.methods.none { it.name == "onDeleteShortcutToggle" } &&
                extClass.methods.none { it.name == "mergeShortcutsFlag3DeleteBit" }
        appendLine(
            if (reflectionToggleAbsent) {
                "OK  reflection toggle helpers removed from extension dex"
            } else {
                "MISSING  extension must not persist shortcuts via reflection toggle"
            },
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