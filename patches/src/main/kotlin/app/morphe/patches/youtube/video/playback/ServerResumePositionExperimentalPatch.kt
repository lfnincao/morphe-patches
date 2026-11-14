/*
 * Copyright 2026 Morphe.
 * https://github.com/lfnincao/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.video.playback

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.playservice.is_20_46_or_greater
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.onCreateHook
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.patches.youtube.video.playerresponse.PlayerParameterBuilder2026Fingerprint
import app.morphe.patches.youtube.video.playerresponse.PlayerParameterBuilderFingerprint

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/playback/ServerResumePositionPatch;"

private const val PARAMETER_VIDEO_ID = 1
private const val PARAMETER_PROTO_BUFFER = 2
private const val PARAMETER_IS_SHORT_AND_OPENING_OR_PLAYING = 13

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

    execute {
        PreferenceScreen.VIDEO.addPreferences(
            noTitleUnsortedPreferenceCategory(
                SwitchPreference("morphe_server_resume_playback_position_experimental", summary = true)
            )
        )

        val fingerprint = if (is_20_46_or_greater) {
            PlayerParameterBuilderFingerprint
        } else {
            PlayerParameterBuilder2026Fingerprint
        }
        fingerprint.method.addInstruction(
            0,
            "invoke-static { p$PARAMETER_PROTO_BUFFER, p$PARAMETER_VIDEO_ID, " +
                "p$PARAMETER_IS_SHORT_AND_OPENING_OR_PLAYING }, " +
                "$EXTENSION_CLASS->onPlayerResponseReceived([BLjava/lang/String;Z)V",
        )

        onCreateHook(EXTENSION_CLASS, "newVideoStarted")
    }
}