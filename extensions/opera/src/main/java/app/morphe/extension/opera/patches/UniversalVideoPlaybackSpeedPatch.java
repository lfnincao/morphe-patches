/*
 * Copyright 2026 Morphe.
 *
 * Injects a document script on each completed page load so HTML5 {@code <video>}
 * and {@code <audio>} elements use a user-selected playback rate. Chromium's
 * built-in overlay speed menu is unchanged; this extends universal page media
 * beyond the default 0.25x–2x set.
 *
 * Hook placement follows recon on Vogelkop-MorphePatches targets
 * (https://github.com/maniesh-NR/Vogelkop-MorphePatches): executes JavaScript
 * via {@code RenderFrameHost} on {@code OperaWebContentsDelegate#loadingStateChanged}.
 */

package app.morphe.extension.opera.patches;

import com.opera.android.browser.chromium.OperaWebContentsDelegate;

import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public class UniversalVideoPlaybackSpeedPatch {
    /**
     * @return If this patch was included during patching.
     */
    @SuppressWarnings("SameReturnValue")
    public static boolean isPatchIncluded() {
        return false;
    }

    /**
     * Patched at build time from the patch option {@code playbackSpeed}.
     */
    public static float getPlaybackSpeed() {
        return 1.0f;
    }

    /**
     * Injection point from {@code OperaWebContentsDelegate.loadingStateChanged}.
     */
    public static void onLoadingStateChanged(Object delegate, boolean isLoading) {
        if (!isPatchIncluded() || isLoading || delegate == null) {
            return;
        }
        try {
            float rate = getPlaybackSpeed();
            String script = buildInjectionScript(rate);
            OperaWebContentsDelegate.patch_executePlaybackSpeedScript(
                    (OperaWebContentsDelegate) delegate, script);
            Logger.printInfo(() -> "Applied HTML5 playback speed script (rate=" + rate + ")");
        } catch (Exception ex) {
            Logger.printException(() -> "onLoadingStateChanged failure", ex);
        }
    }

    private static String buildInjectionScript(float rate) {
        if (rate <= 0f || Float.isNaN(rate) || Float.isInfinite(rate)) {
            rate = 1.0f;
        }
        // Keep the script compact; it runs on every navigation.
        return "(function(){"
                + "var r="
                + rate
                + ";"
                + "function a(){"
                + "try{"
                + "var m=document.querySelectorAll('video,audio');"
                + "for(var i=0;i<m.length;i++){"
                + "try{if(m[i].playbackRate!==r)m[i].playbackRate=r;}catch(e){}"
                + "}"
                + "}catch(e){}"
                + "}"
                + "a();"
                + "try{"
                + "new MutationObserver(a).observe(document.documentElement,{childList:true,subtree:true});"
                + "document.addEventListener('play',function(e){"
                + "try{var t=e.target;if(t&&typeof t.playbackRate==='number'&&t.playbackRate!==r)t.playbackRate=r;}catch(x){}"
                + "},true);"
                + "}catch(e){}"
                + "})();";
    }
}