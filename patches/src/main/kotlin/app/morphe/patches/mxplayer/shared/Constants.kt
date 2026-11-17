package app.morphe.patches.mxplayer.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * MX Player Pro player/UI targets verified on user-supplied Pro 1.93.4 (versionCode 2001002584).
 * Customize-items UI: [Lo20$a] / [Ln20]; toolbar rebuild [ActivityScreen.M5]; shortcuts [LSp0] / [LPp0].
 */
internal object Constants {
    internal const val PACKAGE_NAME = "com.mxtech.videoplayer.pro"
    internal const val TARGET_VERSION = "1.93.4"
    internal const val TARGET_VERSION_CODE = 2001002584
    internal const val MIN_SDK = 24

    /** Bit in shortcuts_flag.3 for delete (0x1000 is screenshot; next flag in Pro 1.93.4). */
    internal const val DELETE_SHORTCUT_BIT = 0x2000

    /** Index in list_shortcuts / list_shortcuts_eng after the stock twelve items. */
    internal const val DELETE_SHORTCUT_LIST_INDEX = 12

    /** Key in list_shortcuts_eng (localized label comes from list_shortcuts resource entry). */
    internal const val DELETE_SHORTCUT_KEY = "delete_file"

    /** LPp0.b type id for the delete toolbar control (must be < 0x10). */
    internal const val DELETE_SHORTCUT_VIEW_TYPE = 12

    internal const val ACTIVITY_SCREEN_CLASS = "Lcom/mxtech/videoplayer/ActivityScreen;"
    internal const val DELETE_DIALOG_INNER_CLASS = "Lcom/mxtech/videoplayer/ActivityScreen\$w;"
    internal const val SHORTCUT_ADAPTER_ON_CLICK_CLASS = "LSp0\$g\$a;"
    internal const val DELETE_PP0_FIELD_NAME = "patchDeleteShortcutPp0"

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