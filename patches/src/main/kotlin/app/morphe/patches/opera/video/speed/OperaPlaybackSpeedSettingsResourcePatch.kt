/*
 * Settings row placement follows BlazeFTL/FTL-Patches ModSettingsPatch.kt (resource XML custom view class).
 */

package app.morphe.patches.opera.video.speed

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.resourceXml
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA_DIRECT

private const val MEDIA_SETTINGS_LAYOUT = "res/layout/fragment_media_settings.xml"
const val OPERA_PLAYBACK_SPEED_SETTINGS_ROW =
    "app.morphe.extension.opera.settings.OperaPlaybackSpeedSettingsRow"

internal val operaPlaybackSpeedSettingsResourcePatch = resourcePatch(
    name = null,
    description = "Inserts Morphe Default playback speed row on Settings → Media (FTL-style custom view).",
) {
    compatibleWith(COMPATIBILITY_OPERA, COMPATIBILITY_OPERA_DIRECT)

    execute {
        resourceXml(MEDIA_SETTINGS_LAYOUT).use { layout ->
            val root = layout.root
            root.insertChild(0, OPERA_PLAYBACK_SPEED_SETTINGS_ROW) {
                setAttribute("android:layout_width", "match_parent")
                setAttribute("android:layout_height", "wrap_content")
            }
        }
    }
}