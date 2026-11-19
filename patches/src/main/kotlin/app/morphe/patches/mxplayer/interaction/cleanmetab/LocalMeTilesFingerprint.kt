package app.morphe.patches.mxplayer.interaction.cleanmetab

import app.morphe.patcher.Fingerprint

private const val LOCAL_ME_PAGE_VIEW_MODEL_CLASS =
    "Lcom/mxtech/videoplayer/ad/subscriptions/ui/metab/viewmodels/LocalMePageViewModel;"

/**
 * Matches the method that builds the Me tab's local-tiles list. Anchored on the
 * tile row labels passed into each row's data class constructor.
 */
internal object LocalMeTilesFingerprint : Fingerprint(
    definingClass = LOCAL_ME_PAGE_VIEW_MODEL_CLASS,
    strings = listOf(
        "MX Share",
        "Private Folder",
        "Video Playlists",
        "Local Network",
        "Music Player",
        "Cloud Drive",
    ),
)