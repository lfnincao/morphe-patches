/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.video.playback

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.onCreateHook
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.patches.youtube.video.playerresponse.Hook
import app.morphe.patches.youtube.video.playerresponse.addPlayerResponseMethodHook
import app.morphe.patches.youtube.video.playerresponse.playerResponseMethodHookPatch

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/playback/ServerResumePositionPatch;"

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
        playerResponseMethodHookPatch,
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        PreferenceScreen.VIDEO.addPreferences(
            noTitleUnsortedPreferenceCategory(
                SwitchPreference("morphe_server_resume_playback_position_experimental", summary = true)
            )
        )

        addPlayerResponseMethodHook(
            Hook.RawPlayerResponse(
                "$EXTENSION_CLASS->onPlayerResponseReceived([BLjava/lang/String;Z)V",
            ),
        )

        onCreateHook(EXTENSION_CLASS, "newVideoStarted")
    }
}