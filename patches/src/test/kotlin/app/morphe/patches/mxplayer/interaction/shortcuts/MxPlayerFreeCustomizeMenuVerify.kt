package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.dex.SdkDexVerifier
import app.morphe.patches.mxplayer.shared.Constants.DELETE_MENU_PREF_KEY_FREE
import app.morphe.patches.mxplayer.shared.Constants.DELETE_MENU_TYPE_FREE
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_EXTENSION_FREE
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory

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

        val applyBtn = findMethod(dexFiles, "Lb3;", "onClick") { it.size == 1 && it[0] == "Landroid/view/View;" }
        val persistsOnApply = applyBtn?.any {
            it is ReferenceInstruction &&
                it.reference.toString().contains("persistDeleteTopBarMenuPref")
        } == true
        appendLine(
            if (persistsOnApply) "OK  Lb3.onClick persists delete top-bar pref on Apply"
            else "MISSING  Lb3.onClick must call persistDeleteTopBarMenuPref",
        )

        val jy1 = findMethod(dexFiles, "Ljy1;", "a") { it.size == 1 && it[0] == "I" }
        val sbd = findMethod(dexFiles, "Lsbd;", "onClick")
        val jy1ByteOk = verifyHookedBooleanCall(jy1, "onCustomizeMenuAction")
        val sbdStealsDelete = sbd?.take(16)?.any {
            it is ReferenceInstruction && it.reference.toString().contains("onCustomizeRowClick")
        } == true
        appendLine(
            if (jy1ByteOk) "OK  Ljy1.a(I) invokes onCustomizeMenuAction with valid move-result"
            else "MISSING  Ljy1.a(I) must invoke-static onCustomizeMenuAction before move-result",
        )
        appendLine(
            if (!sbdStealsDelete) "OK  Lsbd.onClick does not hijack customize picker (toggle stays stock)"
            else "MISSING  remove onCustomizeRowClick from Luad row clicks",
        )
        val deleteFlow =
            dexFiles.flatMap { it.classes }
                .firstOrNull { it.type == DELETE_SHORTCUT_EXTENSION_FREE }
                ?.methods
                ?.firstOrNull { it.name == "deletePlayingFile" }
        val usesYDialog = deleteFlow?.implementation?.instructions?.any {
            it is ReferenceInstruction &&
                it.reference.toString().contains("ActivityScreen\$y")
        } == true
        appendLine(
            if (usesYDialog) "OK  deletePlayingFile constructs ActivityScreen\$y"
            else "MISSING  deletePlayingFile must call ActivityScreen\$y",
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

/** Runs SDK dex verifier on every DEX in the patched free APK (including high classes*.dex). */
fun verifyMxPlayerFreePatchedApkDex(apkFile: File): String {
    val sdk = resolveAndroidSdk()
    val dexDir = createTempDirectory("mx-free-dex-verify").toFile()
    ZipFile(apkFile).use { zip ->
        zip.stream()
            .filter { !it.isDirectory && it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val out = File(dexDir, entry.name)
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
    }
    val dexFiles = dexDir.listFiles()?.filter { it.name.matches(Regex("classes\\d*\\.dex")) }.orEmpty()
    require(dexFiles.isNotEmpty()) { "No DEX in $apkFile" }
  val verifier = SdkDexVerifier(sdk)
    dexFiles.sortedBy { it.name }.forEach { verifier.verifyDexFile(it) }
    return "OK  SDK/ART DEX verification passed for ${dexFiles.size} file(s)\n"
}

/**
 * Ensures a hooked handler uses `invoke-static … Z` immediately before the matching `move-result`,
 * not a leading orphan `move-result` (ART VerifyError on launch).
 */
private fun verifyHookedBooleanCall(
    insns: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>?,
    extensionMethodSuffix: String,
): Boolean {
    if (insns.isNullOrEmpty()) return false
    if (insns.first().opcode == Opcode.MOVE_RESULT) {
        return false
    }
    val window = insns.take(16)
    val moveIdx = window.indexOfFirst { it.opcode == Opcode.MOVE_RESULT }
    if (moveIdx <= 0) return false
    val invoke = window[moveIdx - 1]
    if (invoke.opcode != Opcode.INVOKE_STATIC) return false
    return invoke is ReferenceInstruction &&
        invoke.reference.toString().contains(extensionMethodSuffix)
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