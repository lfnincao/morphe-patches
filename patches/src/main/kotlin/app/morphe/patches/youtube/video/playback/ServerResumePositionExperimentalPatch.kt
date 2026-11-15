/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.video.playback

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import app.morphe.patches.shared.misc.spoof.CreateStreamingDataFingerprint
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.onCreateHook
import app.morphe.patches.youtube.video.information.videoInformationPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import java.lang.ref.WeakReference

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/playback/ServerResumePositionPatch;"

private const val HOOK_METHOD_NAME = "patch_serverResumeOnPlayerResponse"

@Suppress("unused")
val serverResumePositionExperimentalPatch = bytecodePatch(
    name = "Server resume position (experimental)",
    description = "Experimental: when Remember playback position will not restore a video, " +
        "seek once to YouTube's server-side watch-history start position from the player response. " +
        "Local saved positions always take precedence.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        videoInformationPatch,
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    lateinit var constructorRef: WeakReference<MutableMethod>
    lateinit var hookMethod: MutableMethod

    execute {
        PreferenceScreen.VIDEO.addPreferences(
            noTitleUnsortedPreferenceCategory(
                SwitchPreference("morphe_server_resume_playback_position_experimental", summary = true)
            )
        )

        // The constructor of the player response model builder receives the parsed /player response
        // proto of the app (p1). PlaybackStartPositionResolver later reads the resume position from
        // playerResponse.playerConfig.playbackStartConfig (PlayerConfig field 60102073) of that proto.
        // "Spoof video streams" replaces playerConfig in place further down in this same constructor,
        // so the app's original config must be read at the start of the method.
        CreateStreamingDataFingerprint.let {
            val playerResponseClass = it.method.parameterTypes.first().toString()
            // iget-object vX, p1, PlayerResponse->videoDetails
            val videoDetailsField = it.instructionMatches[2].fieldReference
            // iget-object vX, p1, PlayerResponse->playerConfig
            val playerConfigField = it.instructionMatches.last().fieldReference
            val videoDetailsClass = videoDetailsField.type
            val playerConfigClass = playerConfigField.type

            hookMethod = ImmutableMethod(
                it.classDef.type,
                HOOK_METHOD_NAME,
                listOf(ImmutableMethodParameter(playerResponseClass, null, null)),
                "V",
                AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(5),
            ).toMutable().apply {
                addInstructionsWithLabels(
                    0,
                    """
                        invoke-static { }, $EXTENSION_CLASS->isEnabled()Z
                        move-result v0
                        if-eqz v0, :exit
                        if-eqz p0, :exit

                        # Video id (videoDetails.videoId).
                        const/4 v2, 0x0
                        iget-object v1, p0, $videoDetailsField
                        if-eqz v1, :get_player_config
                        iget-object v2, v1, $videoDetailsClass->c:Ljava/lang/String;

                        :get_player_config
                        const/4 v3, 0x0
                        iget-object v1, p0, $playerConfigField
                        if-eqz v1, :call
                        invoke-virtual { v1 }, $playerConfigClass->toByteArray()[B
                        move-result-object v3

                        :call
                        invoke-static { v3, v2 }, $EXTENSION_CLASS->onAppPlayerConfig([BLjava/lang/String;)V

                        :exit
                        return-void
                    """
                )
            }
            it.classDef.methods.add(hookMethod)
            constructorRef = WeakReference(it.method)
        }

        onCreateHook(EXTENSION_CLASS, "newVideoStarted")
    }

    // Inserted in finalize so the instruction indexes that "Spoof video streams" resolved in this
    // constructor during execute are not shifted before it uses them.
    finalize {
        constructorRef.get()!!.apply {
            val instructions = implementation!!.instructions.toList()
            // After the super constructor call, so the hook never runs on an uninitialized 'this'.
            val superInitIndex = instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.INVOKE_DIRECT &&
                    ((instruction as ReferenceInstruction).reference as MethodReference).name == "<init>" &&
                    (instruction as Instruction35c).registerC == implementation!!.registerCount - 2
            }
            val insertIndex = if (superInitIndex >= 0) superInitIndex + 1 else 0
            addInstruction(
                insertIndex,
                "invoke-static/range { p1 .. p1 }, $hookMethod",
            )
        }
    }
}
