/*
 * Copyright 2026 Morphe.
 *
 * Delegates to {@link MorpheOperaPageScriptEvaluator} (main frame
 * {@code RenderFrameHost.d} — not {@code MevqfbP8} / {@code R0}).
 */

package app.morphe.extension.opera.patches;

import android.os.Handler;
import android.os.Looper;

import org.chromium.content.browser.webcontents.MorpheOperaPageScriptEvaluator;
import org.chromium.content_public.browser.WebContents;

import app.morphe.extension.opera.settings.OperaPlaybackSpeedPreferences;
import app.morphe.extension.shared.Logger;

public final class OperaPlaybackSpeedScriptInjector {
    private static final String VERIFY_LOG_TAG = "MorpheOperaPlaybackSpeed";
    private static final long[] RETRY_DELAYS_MS = {250L, 750L, 2000L, 5000L};

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static volatile WebContents lastActiveWebContents;

    private OperaPlaybackSpeedScriptInjector() {
    }

    public static void evaluate(WebContents webContents, String script) {
        if (webContents == null || script == null || script.isEmpty()) {
            return;
        }
        if (OperaPlaybackSpeedPreferences.isScriptInjectionDisabled()) {
            return;
        }
        lastActiveWebContents = webContents;
        try {
            if (webContents.isDestroyed()) {
                return;
            }
            Logger.printInfo(() -> VERIFY_LOG_TAG + ": frame eval (" + script.length() + " chars)");
            boolean ok = MorpheOperaPageScriptEvaluator.evaluateScript(webContents, script);
            if (!ok) {
                Logger.printInfo(() -> VERIFY_LOG_TAG + ": frame evaluateScript declined");
                disableInjectionSafely();
                return;
            }
            OperaPlaybackSpeedPreferences.setScriptInjectionDisabled(false);
        } catch (Throwable throwable) {
            disableInjectionSafely();
            try {
                Logger.printException(() -> VERIFY_LOG_TAG + ": evaluate failed — kill-switch set", throwable);
            } catch (Throwable suppressed) {
                // JNI may rethrow through JniAndroid.handleException; do not propagate.
            }
        }
    }

    public static void scheduleLoadCompleteRetries(float playbackRate) {
        final WebContents webContents = lastActiveWebContents;
        if (webContents == null) {
            return;
        }
        final String script = UniversalVideoPlaybackSpeedPatch.buildInjectionScript(playbackRate);
        for (long delayMs : RETRY_DELAYS_MS) {
            MAIN_HANDLER.postDelayed(() -> {
                if (webContents.isDestroyed()) {
                    return;
                }
                evaluate(webContents, script);
            }, delayMs);
        }
    }

    public static void reevaluateActiveTab(float playbackRate) {
        WebContents webContents = lastActiveWebContents;
        if (webContents == null || webContents.isDestroyed()) {
            return;
        }
        evaluate(webContents, UniversalVideoPlaybackSpeedPatch.buildInjectionScript(playbackRate));
        scheduleLoadCompleteRetries(playbackRate);
    }

    private static void disableInjectionSafely() {
        try {
            OperaPlaybackSpeedPreferences.setScriptInjectionDisabled(true);
        } catch (Throwable ignored) {
            // Best effort.
        }
    }
}