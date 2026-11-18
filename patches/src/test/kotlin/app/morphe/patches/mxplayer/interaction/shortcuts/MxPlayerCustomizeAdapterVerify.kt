package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_LIST_INDEX
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import java.io.File
import java.util.zip.ZipFile

/**
 * Confirms Lo20$a bind/toggle hooks no longer short-circuit stock labels and that delete index
 * 12 is handled in dex (not via per-row extension bind).
 */
internal fun verifyMxPlayerCustomizeAdapter(apkFile: File): String {
    var lines = buildString {
        appendLine("=== customize adapter verify (${apkFile.name}) ===")
        val dexBytes = readLo20AdapterDex(apkFile)
        val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), dexBytes.inputStream())
        val adapter = dex.classes.firstOrNull { it.type == "Lo20\$a;" }
            ?: error("Lo20\$a not in APK")

        val bind = adapter.methods.firstOrNull { it.name == "l" }
            ?: error("Lo20\$a.l missing")
        val bindInsns = bind.implementation!!.instructions.toList()

        val bindHook = bindInsns.take(4).any {
            it is ReferenceInstruction &&
                it.reference.toString().contains("bindCustomizeItem")
        }
        appendLine(
            if (!bindHook) "OK  Lo20\$a.l has no bindCustomizeItem entry hook (stock labels)"
            else "MISSING  bindCustomizeItem still hooks every bind row",
        )

        val hasDeleteRowBranch =
            bindInsns.any { insn ->
                insn.opcode == Opcode.CONST_16 &&
                    (insn as com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction)
                        .narrowLiteral == DELETE_SHORTCUT_LIST_INDEX
            }
        appendLine(
            if (hasDeleteRowBranch) "OK  Lo20\$a.l references index $DELETE_SHORTCUT_LIST_INDEX"
            else "MISSING  no index-$DELETE_SHORTCUT_LIST_INDEX branch in Lo20\$a.l",
        )

        val itemCount = adapter.methods.firstOrNull { it.name == "b" }
        val usesArrayLen =
            itemCount?.implementation?.instructions?.any {
                it.opcode == Opcode.ARRAY_LENGTH
            } == true
        appendLine(
            if (usesArrayLen) "OK  Lo20\$a.b() uses array-length on shortcut labels"
            else "MISSING  Lo20\$a.b() does not look array-backed",
        )

        val toggle = dex.classes.firstOrNull { it.type == "Ln20;" }?.methods?.firstOrNull { it.name == "onCheckedChanged" }
        val toggleHook =
            toggle?.implementation?.instructions?.take(4)?.any {
                it is ReferenceInstruction &&
                    it.reference.toString().contains("onCustomizeToggle")
            } == true
        appendLine(
            if (!toggleHook) "OK  n20.onCheckedChanged has no onCustomizeToggle hook"
            else "MISSING  onCustomizeToggle still intercepts every toggle",
        )

    }
    val failed = lines.lines().any { it.startsWith("MISSING  ") }
    lines += if (failed) "FAILED\n" else "PASSED\n"
    require(lines.contains("PASSED")) { "Customize adapter verification failed:\n$lines" }
    return lines
}

private fun readLo20AdapterDex(apk: File): ByteArray {
    ZipFile(apk).use { zip ->
        for (entry in zip.entries().asSequence()) {
            if (entry.isDirectory || !entry.name.matches(Regex("classes\\d*\\.dex"))) continue
            val bytes = zip.getInputStream(entry).readBytes()
            val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), bytes.inputStream())
            if (dex.classes.any { it.type == "Lo20\$a;" }) {
                return bytes
            }
        }
        error("DEX with Lo20\$a not found in $apk")
    }
}