/*
 * Copyright 2026 Morphe.
 *
 * Injects a document script on each completed page load so HTML5 {@code <video>}
 * and {@code <audio>} elements use a user-selected playback rate.
 *
 * Hook: {@code OperaWebContentsDelegate#loadingStateChanged} (Vogelkop fingerprint).
 * Eval: stock {@code WebContentsImpl.e()} + {@code b:J} + {@code J.N.MevqfbP8(script)}.
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
     * Effective speed for script injection; reads {@link OperaPlaybackSpeedPreferences} on each call.
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

    /**
     * Called from Settings → Media when the user picks a new default speed (same tab, no navigation).
     */
    public static void onDefaultSpeedChangedFromSettings(float rate) {
        if (!isPatchIncluded()) {
            return;
        }
        OperaPlaybackSpeedScriptInjector.reevaluateActiveTab(rate);
    }

    /**
     * Main-world script: sets {@code playbackRate} on current and future {@code video}/{@code audio}.
     * Preference → {@link #getPlaybackSpeed()} → bridge → {@link OperaPlaybackSpeedScriptInjector#evaluate}.
     */
    public static String buildInjectionScript(float rate) {
        if (rate <= 0f || Float.isNaN(rate) || Float.isInfinite(rate)) {
            rate = 1.0f;
        }
        return "(function(){"
                + "var r="
                + rate
                + ";"
                + "function applyRate(el){"
                + "try{"
                + "if(!el||typeof el.playbackRate!=='number')return;"
                + "if(el.playbackRate!==r)el.playbackRate=r;"
                + "}catch(x){}"
                + "}"
                + "function applyAll(){"
                + "try{"
                + "var m=document.querySelectorAll('video,audio');"
                + "for(var i=0;i<m.length;i++)applyRate(m[i]);"
                + "}catch(e){}"
                + "}"
                + "window.__morpheSetPlaybackSpeed=function(n){"
                + "if(typeof n!=='number'||!(n>0))return;"
                + "r=n;window.__morphePlaybackSpeed=r;applyAll();"
                + "};"
                + "window.__morphePlaybackSpeed=r;"
                + "if(window.__morphePlaybackSpeedBooted){"
                + "window.__morpheSetPlaybackSpeed(r);"
                + "return;"
                + "}"
                + "window.__morphePlaybackSpeedBooted=true;"
                + "try{console.log('morphe-playback-speed',r);}catch(e){}"
                + "applyAll();"
                + "try{"
                + "new MutationObserver(function(ms){"
                + "for(var i=0;i<ms.length;i++){"
                + "var ns=ms[i].addedNodes;"
                + "for(var j=0;j<ns.length;j++){"
                + "var n=ns[j];"
                + "if(n.nodeType!==1)continue;"
                + "var t=n.nodeName;"
                + "if(t==='VIDEO'||t==='AUDIO')applyRate(n);"
                + "else if(n.querySelectorAll){"
                + "var q=n.querySelectorAll('video,audio');"
                + "for(var k=0;k<q.length;k++)applyRate(q[k]);"
                + "}"
                + "}"
                + "}"
                + "}).observe(document.documentElement||document,{childList:true,subtree:true});"
                + "var ev=['play','loadeddata','loadedmetadata','ratechange','emptied','canplay'];"
                + "for(var x=0;x<ev.length;x++){"
                + "document.addEventListener(ev[x],function(e){applyRate(e.target);},true);"
                + "}"
                + "}catch(e){}"
                + "})();";
    }
}