package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.dex.SdkDexVerifier
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_EXTENSION_FREE
import app.morphe.patches.mxplayer.shared.Constants.DELETE_SHORTCUT_KEY
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory

/**
 * Static gates for free 3.5.3 delete: Shortcuts list placement + jy1/toolbar delete action.
 */
fun verifyMxPlayerFreeCustomizeMenu(apkFile: File): String {
    val dexFiles = readAllDex(apkFile)
    val lines = buildString {
        appendLine("=== free customize menu verify (${apkFile.name}) ===")

        val menuB = findMethod(dexFiles, "Lcbd;", "b")
        val luadMenuInject = menuB?.any {
            it is ReferenceInstruction &&
                it.reference.toString().contains("appendDeleteCustomizeCandidate")
        } == true
        appendLine(
            if (!luadMenuInject) "OK  no Luad MenuHelper.b() delete inject (path B)"
            else "MISSING  remove appendDeleteCustomizeCandidate from Lcbd.b()",
        )

        val bind = findMethod(dexFiles, "Lrbd\$a;", "onBindViewHolder")
            ?: error("Lrbd\$a.onBindViewHolder missing")
        val bindDelete = bind.any {
            it is ReferenceInstruction &&
                (it.reference.toString().contains(DELETE_SHORTCUT_KEY) ||
                    it.reference.toString().contains("overlayDeleteRowChecked"))
        }
        appendLine(
            if (bindDelete) "OK  Shortcuts list bind wires delete_file row"
            else "MISSING  Lrbd\$a.onBindViewHolder must bind delete shortcut row",
        )

        val toggle = findMethod(dexFiles, "Lqbd;", "onCheckedChanged")
            ?: error("Lqbd.onCheckedChanged missing")
        val toggleDelete = toggle.any {
            it is ReferenceInstruction && it.reference.toString().contains("toggleDeleteShortcut")
        }
        appendLine(
            if (toggleDelete) "OK  Lqbd.onCheckedChanged toggles delete via shortcuts_flag.3"
            else "MISSING  Lqbd.onCheckedChanged must call toggleDeleteShortcut",
        )

        val ya = findMethod(dexFiles, ACTIVITY_SCREEN, "Ya")
        val rebuildYa = ya?.any {
            it is ReferenceInstruction && it.reference.toString().contains("rebuildDeleteShortcutInYa")
        } == true
        appendLine(
            if (rebuildYa) "OK  ActivityScreen.Ya() rebuilds delete toolbar shortcut"
            else "MISSING  ActivityScreen.Ya() must call rebuildDeleteShortcutInYa",
        )

        val toolbarClick = findMethod(dexFiles, "Lcom/mxtech/videoplayer/widget/b\$g\$a;", "onClick")
        val toolbarDelete = toolbarClick?.any {
            it is ReferenceInstruction && it.reference.toString().contains("onToolbarShortcutClick")
        } == true
        appendLine(
            if (toolbarDelete) "OK  toolbar adapter onClick handles delete shortcut"
            else "MISSING  widget b\$g\$a.onClick must call onToolbarShortcutClick",
        )

        val jy1 = findMethodImpl(dexFiles, "Ljy1;", "a") { it.size == 1 && it[0] == "I" }
        val jy1ByteOk = verifyHookedBooleanCall(jy1, "onCustomizeMenuAction")
        appendLine(
            if (jy1ByteOk) "OK  Ljy1.a(I) invokes onCustomizeMenuAction with valid move-result"
            else "MISSING  Ljy1.a(I) must invoke-static onCustomizeMenuAction before move-result",
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

        val applyBtn = findMethod(dexFiles, "Lb3;", "onClick") { it.size == 1 && it[0] == "Landroid/view/View;" }
        val topBarPersist = applyBtn?.any {
            it is ReferenceInstruction &&
                it.reference.toString().contains("persistDeleteTopBarMenuPref")
        } == true
        appendLine(
            if (!topBarPersist) "OK  no Luad top-bar key_show_delete_file persist hook"
            else "MISSING  remove persistDeleteTopBarMenuPref from Lb3.onClick",
        )

        val ext = dexFiles.flatMap { it.classes }.firstOrNull {
            it.type == DELETE_SHORTCUT_EXTENSION_FREE
        } ?: error("DeleteShortcutPatch missing")
        appendLine("OK  extension ${ext.type}")
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
    val boundsReport = verifyDexMethodRegisterBounds(apkFile)
    return buildString {
        appendLine("OK  SDK/ART DEX verification passed for ${dexFiles.size} file(s)")
        append(boundsReport)
    }
}

/**
 * Ensures a hooked handler uses `invoke-static … Z` immediately before the matching `move-result`,
 * not a leading orphan `move-result` (ART VerifyError on launch).
 */
private data class MethodInsnView(
    val registerCount: Int,
    val instructions: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>,
)

private fun verifyHookedBooleanCall(
    method: MethodInsnView?,
    extensionMethodSuffix: String,
): Boolean {
    val insns = method?.instructions
    if (insns.isNullOrEmpty()) return false
    if (insns.first().opcode == Opcode.MOVE_RESULT) {
        return false
    }
    val window = insns.take(16)
    val moveIdx = window.indexOfFirst { it.opcode == Opcode.MOVE_RESULT }
    if (moveIdx <= 0) return false
    val invoke = window[moveIdx - 1]
    if (invoke.opcode != Opcode.INVOKE_STATIC) return false
    val moveInsn = window[moveIdx] as OneRegisterInstruction
    if (moveInsn.registerA >= method.registerCount) return false
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
): List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>? =
    findMethodImpl(dexFiles, type, name, paramsFilter)?.instructions

private fun findMethodImpl(
    dexFiles: List<DexBackedDexFile>,
    type: String,
    name: String,
    paramsFilter: ((List<String>) -> Boolean)? = null,
): MethodInsnView? {
    for (dex in dexFiles) {
        val cls = dex.classes.firstOrNull { it.type == type } ?: continue
        for (method in cls.methods) {
            if (method.name != name) continue
            if (paramsFilter != null && !paramsFilter(method.parameters.map { it.type })) continue
            val impl = method.implementation ?: return null
            return MethodInsnView(impl.registerCount, impl.instructions.toList())
        }
    }
    return null
}