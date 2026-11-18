package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patches.mxplayer.shared.Constants.DELETE_MENU_PREF_KEY_FREE
import app.morphe.patches.mxplayer.shared.Constants.DELETE_MENU_TYPE_FREE
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_EXTENSION_FREE
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import java.io.File
import java.util.zip.ZipFile

/**
 * Proves the patched APK wires Delete into MenuHelper customize lists (not only extension dex).
 */
fun verifyMxPlayerFreeCustomizeMenu(apkFile: File): String {
    val dexFiles = readAllDex(apkFile)
    val lines = buildString {
        appendLine("=== free customize menu verify (${apkFile.name}) ===")

        val menuB = findMethod(dexFiles, "Lcbd;", "b")
            ?: error("Lcbd;->b missing")
        val callsAppend = menuB.any {
            it is ReferenceInstruction &&
                it.reference.toString().contains("appendDeleteCustomizeCandidate")
        }
        appendLine(
            if (callsAppend) "OK  MenuHelper.b() calls appendDeleteCustomizeCandidate"
            else "MISSING  MenuHelper.b() must append delete row",
        )

        val menuC = findMethod(dexFiles, "Lcbd;", "c") { it.size == 1 && it[0] == "I" }
            ?: error("Lcbd;->c(I) missing")
        val callsRegister = menuC.any {
            it is ReferenceInstruction &&
                it.reference.toString().contains("registerDeleteMenuEntry")
        }
        val hasPrefKey = dexFiles.any { dex ->
            dex.classes.any { cls ->
                cls.methods.any { m ->
                    m.implementation?.instructions?.any { insn ->
                        insn is ReferenceInstruction &&
                            insn.reference.toString().contains(DELETE_MENU_PREF_KEY_FREE)
                    } == true
                }
            }
        }
        appendLine(
            if (callsRegister) "OK  MenuHelper.c(I) calls registerDeleteMenuEntry"
            else "MISSING  MenuHelper.c(I) registry hook",
        )
        appendLine(
            if (hasPrefKey) "OK  dex contains pref key $DELETE_MENU_PREF_KEY_FREE"
            else "MISSING  pref key $DELETE_MENU_PREF_KEY_FREE",
        )

        val jy1 = findMethod(dexFiles, "Ljy1;", "a") { it.size == 1 && it[0] == "I" }
        val sbd = findMethod(dexFiles, "Lsbd;", "onClick")
        val jy1Hook = jy1?.take(8)?.any {
            it is ReferenceInstruction && it.reference.toString().contains("onCustomizeMenuAction")
        } == true
        val sbdHook = sbd?.take(8)?.any {
            it is ReferenceInstruction && it.reference.toString().contains("onCustomizeRowClick")
        } == true
        appendLine(
            if (jy1Hook || sbdHook) "OK  delete click wired (jy1=$jy1Hook sbd=$sbdHook)"
            else "MISSING  Ljy1.a(I) and Lsbd.onClick intercepts",
        )

        val ext = dexFiles.flatMap { it.classes }.firstOrNull {
            it.type == DELETE_SHORTCUT_EXTENSION_FREE
        } ?: error("DeleteShortcutPatch missing")
        appendLine("OK  extension ${ext.type}")

        val wrongToolbarHook = findMethod(dexFiles, ACTIVITY_SCREEN, "Ya")?.any {
            it is ReferenceInstruction &&
                it.reference.toString().contains("rebuildDeleteShortcutInYa")
        } == true
        appendLine(
            if (!wrongToolbarHook) "OK  ActivityScreen.Ya() no longer uses rebuildDeleteShortcutInYa"
            else "MISSING  remove legacy Ya() toolbar inject",
        )
    }
    val failed = lines.lines().any { it.startsWith("MISSING  ") }
    val result = lines + if (failed) "FAILED\n" else "PASSED\n"
    require(result.contains("PASSED")) { result }
    return result
}

private const val ACTIVITY_SCREEN = "Lcom/mxtech/videoplayer/ActivityScreen;"

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
    paramsFilter: ((List<String>) -> Boolean)? = null,
): List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>? {
    for (dex in dexFiles) {
        val cls = dex.classes.firstOrNull { it.type == type } ?: continue
        for (method in cls.methods) {
            if (method.name != name) continue
            if (paramsFilter != null && !paramsFilter(method.parameters.map { it.type })) continue
            return method.implementation?.instructions?.toList()
        }
    }
    return null
}