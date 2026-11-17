/*
 * Applies Player delete shortcut to a stock MX Player Pro APK for local verification.
 *
 * Usage: MxPlayerProDeleteShortcutApkBuildKt <input-apk-or-apkm-zip> [output-apk]
 *
 * Uses Patcher 2.3.0 behavior (Morphe Manager 2.5.0). Skips SDK cross-dex verification (base-only APKs are incomplete bundles).
 */
package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkMerger
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.logging.NoOpLogger
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

fun main(arguments: Array<String>) {
    require(arguments.isNotEmpty()) {
        "Usage: MxPlayerProDeleteShortcutApkBuildKt <input-apk-or-apkm-zip> [output-apk]"
    }

    val inputArg = File(arguments[0]).canonicalFile
    require(inputArg.isFile) { "Input does not exist: $inputArg" }

    val apkFile =
        when (inputArg.extension.lowercase()) {
            "zip", "apkm", "xapk", "apks" -> mergeApkmBundle(inputArg)
            "apk" -> inputArg
            else -> error("Unsupported input type: ${inputArg.name}")
        }

    val output = arguments.getOrNull(1)?.let { File(it).canonicalFile }
    output?.parentFile?.mkdirs()

    val workspace = createTempDirectory("mx-pro-delete-shortcut").toFile()
    val patches = setOf(playerDeleteShortcutPatch)

    Patcher(
        PatcherConfig(
            apkFile = apkFile,
            temporaryFilesPath = workspace,
            targetPatcherVersion = "2.3.0",
            validate = false,
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
        output?.let { patched.applyTo(it) }
        println("MX Player Pro delete shortcut patch succeeded (input=$apkFile, workspace=$workspace)")
    }
}

private fun mergeApkmBundle(bundle: File): File {
    val extractDir = Files.createTempDirectory("mx-pro-apkm").toFile()
    check(
        ProcessBuilder("unzip", "-o", bundle.absolutePath, "-d", extractDir.absolutePath)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0,
    ) { "Failed to extract bundle: $bundle" }
    val mergedApk = File(extractDir.parentFile, "mxplayer-pro-merged.apk")
    ApkMerger(NoOpLogger).merge(
        inputFile = extractDir,
        outputFile = mergedApk,
        validateModules = false,
        validateResDir = false,
    )
    println("Merged APKM modules to $mergedApk")
    return mergedApk
}