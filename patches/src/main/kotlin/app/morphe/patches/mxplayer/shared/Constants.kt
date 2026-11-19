package app.morphe.patches.mxplayer.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * MX Player Pro ([Constants.PACKAGE_NAME_PRO]) and free ad flavor ([Constants.PACKAGE_NAME_FREE])
 * targets. Pro: 1.93.4; free Play 3.5.3 (versionCode 2001003563).
 */
internal object Constants {
    internal const val PACKAGE_NAME_PRO = "com.mxtech.videoplayer.pro"
    internal const val PACKAGE_NAME_FREE = "com.mxtech.videoplayer.ad"
    /** @deprecated use [PACKAGE_NAME_PRO] */
    internal const val PACKAGE_NAME = PACKAGE_NAME_PRO

    internal const val TARGET_VERSION_PRO = "1.93.4"
    internal const val TARGET_VERSION_CODE_PRO = 2001002584

    internal const val TARGET_VERSION_FREE = "3.5.3"
    internal const val TARGET_VERSION_CODE_FREE = 2001003563

    internal const val MIN_SDK = 24

    /** Pro: bit in shortcuts_flag.3 for delete (screenshot is 0x1000). */
    internal const val DELETE_SHORTCUT_BIT_PRO = 0x2000

    /** Free 3.5.3: mirror uses 0x2000; delete uses the next free bit. */
    internal const val DELETE_SHORTCUT_BIT_FREE = 0x10000

    /** Fresh-install shortcuts_flag.3 default for free (unchanged stock mask; delete uses menu prefs). */
    internal const val FREE_DEFAULT_SHORTCUTS_MASK = -1

    /** @deprecated use [DELETE_SHORTCUT_BIT_PRO] or package-specific bit */
    internal const val DELETE_SHORTCUT_BIT = DELETE_SHORTCUT_BIT_PRO

    /** 16-bit mask argument for {@code Lo20$a.r} when clearing delete (same pattern as stock ~0x8 = -9). */
    internal const val DELETE_SHORTCUT_CLEAR_MASK = -8193 // 0xdfff

    internal const val LO20_ADAPTER_CLASS = "Lo20\$a;"
    internal const val LO20_ADAPTER_BITWISE_DESCRIPTOR = "(Lo20\$a;I)V"

    /** Index in list_shortcuts / list_shortcuts_eng after the stock twelve items. */
    internal const val DELETE_SHORTCUT_LIST_INDEX = 12

    /** Key in list_shortcuts_eng (localized label comes from list_shortcuts resource entry). */
    internal const val DELETE_SHORTCUT_KEY = "delete_file"

    /** LPp0.b view type for delete toolbar (Pro 1.93.4). */
    internal const val DELETE_SHORTCUT_VIEW_TYPE = 16

    /** {@code Lcbd$b.a} type for delete on free 3.5.3 Customize items (Luad / MenuHelper). */
    internal const val DELETE_MENU_TYPE_FREE = 22

    internal const val DELETE_MENU_PREF_KEY_FREE = "key_show_delete_file"

    internal const val ACTIVITY_SCREEN_CLASS = "Lcom/mxtech/videoplayer/ActivityScreen;"
    internal const val DELETE_DIALOG_INNER_CLASS = "Lcom/mxtech/videoplayer/ActivityScreen\$w;"
    internal const val SHORTCUT_ADAPTER_ON_CLICK_CLASS = "LSp0\$g\$a;"
    internal const val DELETE_PP0_FIELD_NAME = "patchDeleteShortcutPp0"

    /** Morphe extension (FTL ModSettings-style hooks). */
    internal const val DELETE_SHORTCUT_EXTENSION_PRO =
        "Lapp/morphe/extension/mxplayerpro/delete/DeleteShortcutPatch;"

    internal const val DELETE_SHORTCUT_EXTENSION_FREE =
        "Lapp/morphe/extension/mxplayerfree/delete/DeleteShortcutPatch;"

    internal const val MOD_SETTINGS_CLASS =
        "Lapp/morphe/extension/mxplayerfree/modsettings/ModSettings;"

    /** @deprecated use [DELETE_SHORTCUT_EXTENSION_PRO] */
    internal const val DELETE_SHORTCUT_EXTENSION = DELETE_SHORTCUT_EXTENSION_PRO

    internal const val FREE_DELETE_DIALOG_INNER_CLASS = "Lcom/mxtech/videoplayer/ActivityScreen\$y;"
    internal const val FREE_SHORTCUTS_LIST_FIELD = "b0"
    internal const val FREE_TOOLBAR_MODEL_CLASS = "Lb0j;"
    internal const val FREE_FLAGS_PROVIDER_CLASS = "Lb6f;"

    val COMPATIBILITY_MX_PLAYER_PRO = Compatibility(
        packageName = PACKAGE_NAME_PRO,
        name = "MX Player Pro",
        targets = listOf(
            AppTarget(
                version = TARGET_VERSION_PRO,
                versionCode = TARGET_VERSION_CODE_PRO,
                minSdk = MIN_SDK,
            ),
        ),
    )

    val COMPATIBILITY_MX_PLAYER_FREE = Compatibility(
        packageName = PACKAGE_NAME_FREE,
        name = "MX Player",
        targets = listOf(
            AppTarget(
                version = TARGET_VERSION_FREE,
                versionCode = TARGET_VERSION_CODE_FREE,
                minSdk = MIN_SDK,
            ),
        ),
    )
}