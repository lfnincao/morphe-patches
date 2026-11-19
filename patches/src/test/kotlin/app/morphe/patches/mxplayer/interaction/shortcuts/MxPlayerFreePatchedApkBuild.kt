/*
 * Applies free MX Player patches (delete shortcut, ad removal, patched install) to a stock APK.
 *
 * Usage: MxPlayerFreePatchedApkBuildKt <input.apk> [output.apk]
 */
package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patches.mxplayer.ad.mxPlayerRemoveAdsPatch
import app.morphe.patches.mxplayer.interaction.cleanmetab.cleanMeTabPatch
import app.morphe.patches.mxplayer.misc.fix.mxPlayerPatchedInstallPatch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

fun main(arguments: Array<String>) {
    require(arguments.isNotEmpty()) {
        "Usage: MxPlayerFreePatchedApkBuildKt <input.apk> [output.apk]"
    }
    val apkFile = File(arguments[0]).canonicalFile
    require(apkFile.isFile) { "Input does not exist: $apkFile" }
    val output = arguments.getOrNull(1)?.let { File(it).canonicalFile }
    output?.parentFile?.mkdirs()

    val workspace = createTempDirectory("mx-free-patch").toFile()
    val patches = setOf(
        mxPlayerPatchedInstallPatch,
        playerDeleteShortcutPatch,
        mxPlayerRemoveAdsPatch,
        cleanMeTabPatch,
    )

    Patcher(
        PatcherConfig(
            apkFile = apkFile,
            temporaryFilesPath = workspace,
            targetPatcherVersion = "2.3.0",
            validate = true,
        ),
    ).use { patcher ->
        patcher += patches
        runBlocking {
            patcher().collect { result ->
                result.exception?.let { throw it }
                println("Applied: ${result.patch.name}")
            }
        }
        val patched = patcher.get()
        output?.let { out ->
            patched.applyTo(out)
            println(verifyMxPlayerFreePatchedApkDex(out))
            println(verifyMxPlayerFreeCustomizeMenu(out))
            println(verifyMxPlayerExtensionInvokes(out))
        }
        println("MX Player free patch succeeded (input=$apkFile)")
    }
}