/*
 * Ported from Vogelkop-MorphePatches (MIT) — app/template/patches/opera/manifest/RemoveAdTrackingPermissionsPatch.kt
 */

package app.morphe.patches.opera.manifest

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA
import app.morphe.patches.opera.shared.COMPATIBILITY_OPERA_DIRECT
import org.w3c.dom.Element
import org.w3c.dom.Node

@Suppress("unused")
val operaRemoveAdTrackingPermissionsPatch = resourcePatch(
    name = "Remove ad & tracking permissions",
    description = "Drops ad-profiling and tracking permissions from the manifest: " +
        "Privacy Sandbox (ACCESS_ADSERVICES_*), Google Advertising ID, Play " +
        "install-referrer attribution, basic phone state and Huawei store junk.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_OPERA, COMPATIBILITY_OPERA_DIRECT)

    execute {
        val permissionNames = setOf(
            "android.permission.ACCESS_ADSERVICES_AD_ID",
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            "android.permission.ACCESS_ADSERVICES_TOPICS",
            "com.google.android.gms.permission.AD_ID",
            "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
            "android.permission.READ_BASIC_PHONE_STATE",
            "com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA",
        )
        val propertyNames = setOf(
            "android.adservices.AD_SERVICES_CONFIG",
        )

        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement

            val permissionTags = setOf("uses-permission", "uses-permission-sdk-23")
            val toRemove = mutableListOf<Node>()
            val removedNames = mutableListOf<String>()

            val children = manifest.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i) as? Element ?: continue
                val name = node.getAttribute("android:name")
                    .ifBlank { node.getAttributeNS(ANDROID_XML_NAMESPACE, "name") }
                when {
                    node.tagName in permissionTags && name in permissionNames -> {
                        toRemove += node
                        removedNames += name
                    }
                    node.tagName == "property" && name in propertyNames -> {
                        toRemove += node
                        removedNames += name
                    }
                }
            }

            if (removedNames.isEmpty()) {
                throw PatchException(
                    "No ad/tracking permissions found in AndroidManifest.xml. " +
                        "Is this an unsupported Opera variant?",
                )
            }

            toRemove.forEach(manifest::removeChild)
            println(
                "Opera permissions: removed ${removedNames.size} manifest entr(ies): " +
                    removedNames.joinToString(", "),
            )
        }
    }
}

private const val ANDROID_XML_NAMESPACE = "http://schemas.android.com/apk/res/android"