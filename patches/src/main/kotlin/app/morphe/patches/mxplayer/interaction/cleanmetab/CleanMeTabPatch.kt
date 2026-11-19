package app.morphe.patches.mxplayer.interaction.cleanmetab

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_FREE

/**
 * Technique credit: [FTL-Patches](https://github.com/BlazeFTL/FTL-Patches) (BlazeFTL) — Mod Settings Me tab
 * tile and menu hiders, adapted for Morphe with per-toggle defaults off.
 */
@Suppress("unused")
val cleanMeTabPatch = bytecodePatch(
    name = "Clean Me Tab",
    description = "Adds Mod Settings switches (default off) to hide Me tab promo rows, quick-access tiles, " +
        "and Private Folder / File Transfer / Add to Playlist entries when enabled.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_FREE)

    dependsOn(cleanMeTabLayoutsPatch, cleanMeTabTilesPatch)
}