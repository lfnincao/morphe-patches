/*
 * Copyright 2026 Morphe.
 *
 * Page JS is evaluated via {@link MorpheOperaPageScriptEvaluator} in the
 * {@code org.chromium.content.browser.webcontents} package (legal access to
 * package-private {@code WebContentsImpl#getNativePointer()} / field {@code b:J}).
 *
 * {@link org.chromium.content_public.browser.WebContents#R0} → {@code MseJ7A4a} (devtools only).
 */

package app.morphe.extension.opera.patches;

import org.chromium.content.browser.webcontents.MorpheOperaPageScriptEvaluator;
import org.chromium.content_public.browser.WebContents;

import app.morphe.extension.opera.settings.OperaPlaybackSpeedPreferences;
import app.morphe.extension.shared.Logger;

public final class OperaPlaybackSpeedScriptInjector {
    private static final String VERIFY_LOG_TAG = "MorpheOperaPlaybackSpeed";

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
            Logger.printInfo(() -> VERIFY_LOG_TAG + ": page eval (" + script.length() + " chars)");
            boolean ok = MorpheOperaPageScriptEvaluator.evaluateScript(webContents, script);
            if (!ok) {
                Logger.printInfo(() -> VERIFY_LOG_TAG + ": MorpheOperaPageScriptEvaluator declined");
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

    public static void reevaluateActiveTab(float playbackRate) {
        WebContents webContents = lastActiveWebContents;
        if (webContents == null || webContents.isDestroyed()) {
            return;
        }
        evaluate(webContents, UniversalVideoPlaybackSpeedPatch.buildInjectionScript(playbackRate));
    }

    private static void disableInjectionSafely() {
        try {
            OperaPlaybackSpeedPreferences.setScriptInjectionDisabled(true);
        } catch (Throwable ignored) {
            // Best effort.
        }
    }
}