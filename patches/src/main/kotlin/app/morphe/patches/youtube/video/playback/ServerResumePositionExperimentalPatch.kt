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
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import app.morphe.patches.shared.misc.proto.hookElement
import app.morphe.patches.shared.misc.spoof.CreateStreamingDataFingerprint
import app.morphe.patches.youtube.misc.proto.elementProtoParserHookPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.onCreateHook
import app.morphe.patches.youtube.video.information.videoInformationPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import java.lang.ref.WeakReference
import java.util.logging.Logger

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/playback/ServerResumePositionPatch;"

private const val WATCH_PROGRESS_CLASS =
    "Lapp/morphe/extension/youtube/patches/playback/ServerWatchProgress;"

private const val HOOK_METHOD_NAME = "patch_serverResumeOnPlayerResponse"

private const val PANEL_HOOK_METHOD_NAME = "patch_serverResumeOnPlaylistPanelVideo"

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
        elementProtoParserHookPatch,
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

        // Watch progress (red bar) of the thumbnails of Litho lists, such as feeds and related videos.
        // Playlist navigation starts videos without a start time, and their player response has none.
        hookElement("$WATCH_PROGRESS_CLASS->onElement")

        // Watch progress of the rows of the playlist panel on the watch page, which does not use Litho.
        // The bind method of a row reads PlaylistPanelVideoRenderer.thumbnailOverlays with a static helper
        // that returns the ThumbnailOverlayResumePlaybackRenderer (or null), and passes it to the
        // binder of the resume progress bar.
        var panelHooks = 0
        PlaylistPanelVideoResumeFingerprint.matchAll().forEach { match ->
            val method = match.method
            val instructions = method.implementation!!.instructions.toList()
            // iput-object vId, vHolder, Holder->videoId:Ljava/lang/String;
            val videoIdIput = match.instructionMatches.last()
            val videoIdField = videoIdIput.fieldReference
            val holderRegister = (videoIdIput.instruction as
                com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction).registerB

            // invoke-static { vList }, Lhelper;->x(Ljava/util/List;)LResumeOverlay;
            // move-result-object vOverlay
            // ...
            // invoke-virtual { vBinder, vOverlay }, LBinder;->x(LResumeOverlay;)V
            val getterIndex = instructions.indices.firstOrNull { index ->
                val instruction = instructions[index]
                if (instruction.opcode != Opcode.INVOKE_STATIC) return@firstOrNull false
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                if (reference.parameterTypes.map { it.toString() } != listOf("Ljava/util/List;")) {
                    return@firstOrNull false
                }
                if (instructions.getOrNull(index + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT) {
                    return@firstOrNull false
                }
                val overlayType = reference.returnType
                (index + 2 until minOf(index + 20, instructions.size)).any { next ->
                    val call = instructions[next]
                    call.opcode == Opcode.INVOKE_VIRTUAL && ((call as ReferenceInstruction).reference as MethodReference)
                        .let { it.returnType == "V" && it.parameterTypes.map { type -> type.toString() } == listOf(overlayType) }
                }
            } ?: return@forEach

            val getter = (instructions[getterIndex] as ReferenceInstruction).reference as MethodReference
            val overlayType = getter.returnType
            val overlayRegister = (instructions[getterIndex + 1] as OneRegisterInstruction).registerA
            if (holderRegister > 15 || overlayRegister > 15) return@forEach

            // The binder of the resume bar reads percentDurationWatched, the only int it reads of the overlay.
            val binderCall = instructions.subList(getterIndex + 2, instructions.size).first { call ->
                call.opcode == Opcode.INVOKE_VIRTUAL && ((call as ReferenceInstruction).reference as MethodReference)
                    .parameterTypes.map { it.toString() } == listOf(overlayType)
            }
            val binder = (binderCall as ReferenceInstruction).reference as MethodReference
            val binderMethod = classDefBy(binder.definingClass).methods.first { candidate ->
                candidate.name == binder.name && candidate.returnType == "V" &&
                    candidate.parameterTypes.map { it.toString() } == listOf(overlayType)
            }
            val percentField = binderMethod.implementation!!.instructions.firstNotNullOfOrNull { instruction ->
                if (instruction.opcode != Opcode.IGET) return@firstNotNullOfOrNull null
                ((instruction as ReferenceInstruction).reference as FieldReference)
                    .takeIf { it.definingClass == overlayType && it.type == "I" }
            } ?: return@forEach

            val holderClass = mutableClassDefBy(method.definingClass)
            val helper = ImmutableMethod(
                holderClass.type,
                PANEL_HOOK_METHOD_NAME,
                listOf(
                    ImmutableMethodParameter(videoIdField.definingClass, null, null),
                    ImmutableMethodParameter(overlayType, null, null),
                ),
                "V",
                AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(4),
            ).toMutable().apply {
                addInstructionsWithLabels(
                    0,
                    """
                        if-eqz p0, :exit
                        iget-object v0, p0, $videoIdField
                        const/4 v1, -0x1
                        if-eqz p1, :call
                        iget v1, p1, $percentField
                        :call
                        invoke-static { v0, v1 }, $WATCH_PROGRESS_CLASS->onPlaylistPanelVideo(Ljava/lang/String;I)V
                        :exit
                        return-void
                    """
                )
            }
            if (holderClass.methods.none { it.name == PANEL_HOOK_METHOD_NAME }) {
                holderClass.methods.add(helper)
            }
            match.method.addInstruction(
                getterIndex + 2,
                "invoke-static { v$holderRegister, v$overlayRegister }, $helper",
            )
            panelHooks++
        }
        if (panelHooks == 0) {
            Logger.getLogger(this::class.java.name).warning(
                "Could not find the playlist panel resume overlay. Playlist panel watch progress is not used."
            )
        }

        // Debug log of the start position YouTube itself resolves (PlaybackStartPositionResolver).
        PlaybackStartPositionResolverFingerprint.method.apply {
            implementation!!.instructions.withIndex()
                .filter { (_, instruction) -> instruction.opcode == Opcode.RETURN_OBJECT }
                .map { (index, instruction) -> index to (instruction as OneRegisterInstruction).registerA }
                .asReversed()
                .forEach { (index, register) ->
                    addInstruction(
                        index,
                        "invoke-static/range { v$register .. v$register }, " +
                            "$EXTENSION_CLASS->onYouTubeStartPositionResolved(Ljava/lang/Long;)V",
                    )
                }
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

/**
 * PlaybackStartPositionResolver: picks the start position of a video from the start time of its
 * navigation (watchEndpoint.startTimeSeconds), the client's saved position and the player response.
 */
internal object PlaybackStartPositionResolverFingerprint : app.morphe.patcher.Fingerprint(
    returnType = "Ljava/lang/Long;",
    parameters = listOf("L", "L", "L"),
    filters = listOf(
        app.morphe.patcher.string(
            "clientPlaybackStartPosition=",
            comparison = app.morphe.patcher.StringComparisonType.CONTAINS,
        ),
    ),
)

/**
 * Binds a row of the playlist panel on the watch page (PlaylistPanelVideoRenderer, not Litho),
 * and stores its video id in a field of the view holder.
 */
internal object PlaylistPanelVideoResumeFingerprint : app.morphe.patcher.Fingerprint(
    returnType = "V",
    parameters = listOf("L", "Ljava/lang/Object;"),
    filters = listOf(
        app.morphe.patcher.string("PLAYLIST_CURRENT_VIDEO_MONITOR"),
        // Video id.
        app.morphe.patcher.fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Ljava/lang/String;",
            location = app.morphe.patcher.InstructionLocation.MatchAfterWithin(10),
        ),
        app.morphe.patcher.fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            type = "Ljava/lang/String;",
            location = app.morphe.patcher.InstructionLocation.MatchAfterImmediately(),
        ),
    ),
)
