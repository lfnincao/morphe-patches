/*
 * Verifies patched Opera DEX for UniversalVideoPlaybackSpeedPatch.getPlaybackSpeed().
 *
 * Usage: OperaPlaybackSpeedDexVerifyKt <patched.apk>
 */
package app.morphe.patches.opera.video.speed

import app.morphe.patcher.dex.SdkDexVerifier
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.Opcode
import java.io.File
import java.util.zip.ZipFile

private const val EXTENSION_TYPE =
    "Lapp/morphe/extension/opera/patches/UniversalVideoPlaybackSpeedPatch;"
private const val INJECTOR_TYPE =
    "Lapp/morphe/extension/opera/patches/OperaPlaybackSpeedScriptInjector;"
private const val RENDER_FRAME_HOST_TYPE =
    "Lorg/chromium/content_public/browser/RenderFrameHost;"
private const val WEB_AUTH_RESULT_TYPE =
    "Lorg/chromium/content_public/browser/RenderFrameHost${'$'}WebAuthSecurityChecksResults;"
private const val OPERA_APPLICATION_TYPE = "Lcom/opera/android/OperaApplication;"
private const val SHARED_UTILS_TYPE = "Lapp/morphe/extension/shared/Utils;"
private const val BASE_SETTINGS_TYPE = "Lapp/morphe/extension/shared/settings/BaseSettings;"

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: OperaPlaybackSpeedDexVerifyKt <patched.apk>" }
    val apk = File(args[0]).canonicalFile
    require(apk.isFile) { "Missing APK: $apk" }

    val dexFiles = mutableListOf<File>()
    val smaliOut = StringBuilder()
    var found = false
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val bytes = zip.getInputStream(entry).readBytes()
                val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), bytes.inputStream())
                val classDef = dex.classes.firstOrNull { it.type == EXTENSION_TYPE } ?: return@forEach
                found = true
                val method = classDef.methods.firstOrNull { it.name == "getPlaybackSpeed" }
                    ?: error("getPlaybackSpeed not found in $EXTENSION_TYPE")
                val impl = method.implementation
                    ?: error("getPlaybackSpeed has no implementation")
                val instructions = impl.instructions.toList()
                check(instructions.size >= 2) {
                    "getPlaybackSpeed too short (${instructions.size} insns): $instructions"
                }
                val first = instructions.first()
                val last = instructions.last()
                check(first.opcode == Opcode.CONST || first.opcode == Opcode.CONST_HIGH16) {
                    "First instruction must load a float constant, got ${first.opcode}"
                }
                check(last.opcode == Opcode.RETURN) {
                    "Last instruction must be return, got ${last.opcode}"
                }
                smaliOut.appendLine("# ${entry.name} :: $EXTENSION_TYPE->getPlaybackSpeed()")
                instructions.forEach { ins -> smaliOut.appendLine(instructionToSmali(ins)) }
                smaliOut.appendLine()
                val tmp = File.createTempFile("opera-verify-", ".dex")
                tmp.writeBytes(bytes)
                dexFiles += tmp
            }
    }
    check(found) { "Extension class not in patched APK" }

    verifyExtensionMethodExists(apk, INJECTOR_TYPE, "evaluate")
    verifyInjectorDoesNotCallWebAuth(apk)
    listOf(
        "patch_executePlaybackSpeedScript" to "Lcom/opera/android/browser/chromium/OperaWebContentsDelegate;",
    ).forEach { (name, type) ->
        verifyHelperMethod(apk, type, name, smaliOut)
    }

    verifySpeedPatchDoesNotHookOperaApplication(apk)
    verifyExtensionNeverReferencesSettings(apk)

    val sdk = System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME required")
    val verifier = SdkDexVerifier(File(sdk))
    dexFiles.forEach { verifier.verifyDexFile(it) }
    println("ART/SDK DEX verification passed for ${dexFiles.size} file(s) containing extension.")
    println("--- getPlaybackSpeed() smali ---")
    print(smaliOut)
}

private fun instructionToSmali(ins: Instruction): String {
    return when (ins.opcode) {
        Opcode.CONST, Opcode.CONST_HIGH16 -> {
            val reg = (ins as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA
            val lit = (ins as com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction).narrowLiteral
            "${ins.opcode.name.lowercase().replace('_', '/')} v$reg, 0x${Integer.toHexString(lit)}"
        }
        Opcode.RETURN -> {
            val reg = (ins as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA
            "return v$reg"
        }
        else -> ins.opcode.toString()
    }
}

private fun verifySpeedPatchDoesNotHookOperaApplication(apk: File) {
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val onCreate = dex.classes
                    .firstOrNull { it.type == OPERA_APPLICATION_TYPE }
                    ?.methods
                    ?.firstOrNull { it.name == "onCreate" && it.parameterTypes.isEmpty() }
                    ?: return@forEach
                val impl = onCreate.implementation ?: return@forEach
                impl.instructions.forEach { instruction ->
                    val reference = instruction as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
                    val methodRef = reference?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference
                        ?: return@forEach
                    val owner = methodRef.definingClass
                    check(
                        owner != SHARED_UTILS_TYPE &&
                            owner != "Lapp/morphe/extension/opera/OperaProcessGuard;" &&
                            !owner.startsWith("Lapp/morphe/extension/shared/settings/"),
                    ) {
                        "OperaApplication.onCreate must not call Morphe context/settings in speed-only patch " +
                            "(found $owner->${methodRef.name} in ${entry.name})"
                    }
                }
            }
    }
    println("Static check passed: OperaApplication.onCreate has no Morphe context/settings hooks.")
}

private fun verifyExtensionNeverReferencesSettings(apk: File) {
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val speedClass = dex.classes.firstOrNull { it.type == EXTENSION_TYPE } ?: return@forEach
                speedClass.methods.forEach { method ->
                    method.implementation?.instructions?.forEach { instruction ->
                        val reference = instruction as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
                        val typeRef = reference?.reference as? com.android.tools.smali.dexlib2.iface.reference.TypeReference
                        val methodRef = reference?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference
                        val touched = typeRef?.type ?: methodRef?.definingClass
                        check(touched != BASE_SETTINGS_TYPE && !touched.orEmpty().contains("/settings/")) {
                            "UniversalVideoPlaybackSpeedPatch must not reference settings ($touched)"
                        }
                    }
                }
            }
    }
    println("Static check passed: playback speed extension does not reference settings.")
}

private fun verifyInjectorDoesNotCallWebAuth(apk: File) {
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val injector = dex.classes.firstOrNull { it.type == INJECTOR_TYPE } ?: return@forEach
                injector.methods.forEach { method ->
                    method.implementation?.instructions?.forEach { instruction ->
                        val reference = instruction as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
                        val methodRef = reference?.reference as? com.android.tools.smali.dexlib2.iface.reference.MethodReference
                        val typeRef = reference?.reference as? com.android.tools.smali.dexlib2.iface.reference.TypeReference
                        if (methodRef != null) {
                            check(methodRef.definingClass != RENDER_FRAME_HOST_TYPE) {
                                "Injector must not call RenderFrameHost " +
                                    "(${methodRef.definingClass}->${methodRef.name})"
                            }
                        }
                        val touched = typeRef?.type ?: methodRef?.definingClass
                        check(touched != WEB_AUTH_RESULT_TYPE) {
                            "Injector references WebAuthSecurityChecksResults"
                        }
                    }
                }
            }
    }
    println("Static check passed: injector does not reference WebAuth RenderFrameHost APIs.")
}

private fun verifyExtensionMethodExists(apk: File, type: String, name: String) {
    ZipFile(apk).use { zip ->
        val found = zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .any { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                dex.classes.any { classDef ->
                    classDef.type == type && classDef.methods.any { it.name == name }
                }
            }
        check(found) { "$type->$name missing from patched APK" }
    }
    println("Static check passed: $type->$name present in DEX.")
}

private fun verifyHelperMethod(apk: File, type: String, name: String, out: StringBuilder) {
    ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    zip.getInputStream(entry).readBytes().inputStream(),
                )
                val classDef = dex.classes.firstOrNull { it.type == type } ?: return@forEach
                val method = classDef.methods.firstOrNull { it.name == name }
                    ?: error("$type->$name not found in patched APK")
                val instructions = method.implementation?.instructions?.toList()
                    ?: error("$type->$name has no implementation")
                check(instructions.isNotEmpty()) { "$type->$name is empty" }
                out.appendLine("# ${entry.name} :: $type->$name")
                instructions.forEach { ins -> out.appendLine("  $ins") }
                out.appendLine()
            }
    }
}