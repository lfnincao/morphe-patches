package app.morphe.patches.mxplayer.interaction.cleanmetab

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.mxplayer.shared.Constants.COMPATIBILITY_MX_PLAYER_FREE
import org.w3c.dom.Element

internal var networkStreamIconId: Int = 0
    private set
internal var networkStreamTitleId: Int = 0
    private set
internal var mxShareMenuId: Int = 0
    private set
internal var privateFolderMenuId: Int = 0
    private set

private fun ResourcePatchContext.resourceId(type: String, name: String): Int {
    document("res/values/public.xml").use { doc ->
        val nodes = doc.documentElement.getElementsByTagName("public")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            if (element.getAttribute("type") == type && element.getAttribute("name") == name) {
                return element.getAttribute("id").substring(2).toLong(16).toInt()
            }
        }
    }
    error("$type/$name not found in public.xml - it may have been renamed or removed in this build.")
}

internal val resolveNetworkStreamResourcesPatch = resourcePatch(
    name = null,
    description = "Resolves the native icon/string resource ids reused for the Network Stream tile by name.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_FREE)

    execute {
        networkStreamIconId = resourceId("drawable", "ic_me_tab_cloud_drive")
        networkStreamTitleId = resourceId("string", "network_stream")
    }
}

internal val resolveMeTabMenuIdsPatch = resourcePatch(
    name = null,
    description = "Resolves the multi-select menu item ids for File Transfer and Private Folder by name.",
) {
    compatibleWith(COMPATIBILITY_MX_PLAYER_FREE)

    execute {
        mxShareMenuId = resourceId("id", "mx_share")
        privateFolderMenuId = resourceId("id", "option_private_folder")
    }
}