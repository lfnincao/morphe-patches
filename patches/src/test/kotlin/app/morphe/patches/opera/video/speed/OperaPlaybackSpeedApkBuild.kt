/*
 * Applies Opera patches for local device/emulator verification.
 *
 * Usage: OperaPlaybackSpeedApkBuildKt <stock-apk> <mode> [signed-output.apk]
 *   mode: speed-only | speed-clone | manager-default
 */
package app.morphe.patches.opera.video.speed

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.apk.ApkUtils.applyTo
import kotlin.io.path.copyTo
import app.morphe.patcher.patch.loadPatchesFromJar
import app.morphe.patches.all.misc.clone.cloneAppPatch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory

fun main(arguments: Array<String>) {
    require(arguments.size in 2..3) {
        "Usage: OperaPlaybackSpeedApkBuildKt <stock-apk> <speed-only|speed-clone|manager-default> [output.apk]"
    }
    val input = File(arguments[0]).canonicalFile
    require(input.isFile) { "Input APK missing: $input" }
    val mode = arguments[1]
    val bundle = File(
        System.getProperty("morpheBundle")
            ?: System.getenv("MORPHE_BUNDLE")
            ?: error("Set morpheBundle or MORPHE_BUNDLE to patches-*.mpp"),
    )
    require(bundle.isFile) { "Bundle missing: $bundle" }

    val patches = when (mode) {
        "speed-only" -> setOf(operaUniversalVideoPlaybackSpeedPatch)
        "speed-clone" -> setOf(operaUniversalVideoPlaybackSpeedPatch, cloneAppPatch)
        "manager-default" -> loadPatchesFromJar(setOf(bundle)).filter { patch ->
            val packages = runCatching { patch.compatibility }.getOrNull()
            packages == null || packages.any { it.packageName == "com.opera.browser" }
        }.toSet()
        else -> error("Unknown mode: $mode")
    }

    val workspace = createTempDirectory("opera-$mode").toFile()
    Patcher(
        PatcherConfig(
            apkFile = input,
            temporaryFilesPath = workspace,
            targetPatcherVersion = "2.3.0",
            validate = false,
        ),
    ).use { patcher ->
        patcher += patches
        runBlocking {
            patcher().collect { result ->
                val ex = result.exception
                if (ex != null) {
                    System.err.println("${result.patch.name} FAILED: ${ex.stackTraceToString()}")
                    throw ex
                }
                println("Applied: ${result.patch.name}")
            }
        }
        arguments.getOrNull(2)?.let { outPath ->
            val out = File(outPath).canonicalFile
            out.parentFile?.mkdirs()
            input.toPath().copyTo(out.toPath(), overwrite = true)
            patcher.get().applyTo(out)
            val keystore = File(System.getProperty("signingKeystore", "/tmp/opera-debug.keystore"))
            ApkUtils.signApk(
                out,
                out,
                "debug",
                ApkUtils.KeyStoreDetails(
                    keyStore = keystore,
                    keyStorePassword = System.getProperty("keystorePassword", "android"),
                    alias = System.getProperty("keystoreAlias", "androiddebugkey"),
                    password = System.getProperty("keyPassword", "android"),
                ),
                minSdkVersion = 32,
            )
            println("Signed patched APK: $out (${out.length()} bytes)")
        }
        println("Opera $mode patch succeeded (workspace=$workspace)")
    }
}