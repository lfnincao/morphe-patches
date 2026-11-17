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

import app.morphe.extension.opera.settings.OperaPlaybackSpeedPreferences;
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
     * Initial default from the Morphe patch option {@code playbackSpeed} (build time only).
     */
    public static float getPatchDefaultPlaybackSpeed() {
        return 1.0f;
    }

    /**
     * Effective speed for script injection; reads {@link SharedPreferences} on each call.
     */
    public static float getPlaybackSpeed() {
        return OperaPlaybackSpeedPreferences.resolvePlaybackSpeed(getPatchDefaultPlaybackSpeed());
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
        // Compact script; runs on every navigation in the page main world.
        return "(function(){"
                + "var r="
                + rate
                + ";"
                + "function s(e){"
                + "try{if(e&&typeof e.playbackRate==='number'&&e.playbackRate!==r)e.playbackRate=r;}catch(x){}"
                + "}"
                + "function a(){"
                + "try{var m=document.querySelectorAll('video,audio');"
                + "for(var i=0;i<m.length;i++)s(m[i]);"
                + "}catch(e){}"
                + "}"
                + "try{console.log('morphe-playback-speed-applied',r);}catch(e){}"
                + "a();"
                + "try{"
                + "new MutationObserver(function(ms){"
                + "for(var i=0;i<ms.length;i++){"
                + "var ns=ms[i].addedNodes;"
                + "for(var j=0;j<ns.length;j++){"
                + "var n=ns[j];"
                + "if(n.nodeType!==1)continue;"
                + "var t=n.nodeName;"
                + "if(t==='VIDEO'||t==='AUDIO')s(n);"
                + "else if(n.querySelectorAll){"
                + "var q=n.querySelectorAll('video,audio');"
                + "for(var k=0;k<q.length;k++)s(q[k]);"
                + "}"
                + "}"
                + "}"
                + "}).observe(document.documentElement,{childList:true,subtree:true});"
                + "var ev=['play','loadedmetadata','ratechange'];"
                + "for(var x=0;x<ev.length;x++){"
                + "document.addEventListener(ev[x],function(e){s(e.target);},true);"
                + "}"
                + "}catch(e){}"
                + "})();";
    }
}