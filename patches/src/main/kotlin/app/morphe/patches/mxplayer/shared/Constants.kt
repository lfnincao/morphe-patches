package app.morphe.patches.mxplayer.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * MX Player Pro player/UI targets. FTL-Patches (BlazeFTL/FTL-Patches) pins the free MX Player
 * (com.mxtech.videoplayer.ad) player build at 3.4.0 (versionCode 2001003550); Pro shares the same
 * player stack and customize-items implementation under com.mxtech.videoplayer.*.
 */
internal object Constants {
    internal const val PACKAGE_NAME = "com.mxtech.videoplayer.pro"
    internal const val TARGET_VERSION = "3.4.0"
    internal const val TARGET_VERSION_CODE = 2001003550
    internal const val MIN_SDK = 24

    /** Bit in shortcuts_flag.3 for the delete shortcut (0x8000 audio effect is the prior item). */
    internal const val DELETE_SHORTCUT_BIT = 0x10000

    /** Key in list_shortcuts_eng and customize-items switches. */
    internal const val DELETE_SHORTCUT_KEY = "delete_file"

    /** wuh.f75512b type id for the delete toolbar button (12/13 are unused). */
    internal const val DELETE_SHORTCUT_VIEW_TYPE = 19

    internal const val ACTIVITY_SCREEN_CLASS = "Lcom/mxtech/videoplayer/ActivityScreen;"
    internal const val DELETE_DIALOG_INNER_CLASS = "Lcom/mxtech/videoplayer/ActivityScreen\$w;"
    internal const val SHORTCUT_ADAPTER_ON_CLICK_CLASS = "Lcom/mxtech/videoplayer/widget/b\$g\$a;"
    internal const val DELETE_WUH_FIELD_NAME = "patchDeleteShortcutWuh"

    val COMPATIBILITY_MX_PLAYER_PRO = Compatibility(
        packageName = PACKAGE_NAME,
        name = "MX Player Pro",
        targets = listOf(
            AppTarget(
                version = TARGET_VERSION,
                versionCode = TARGET_VERSION_CODE,
                minSdk = MIN_SDK,
            ),
        ),
    )
}