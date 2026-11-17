/*
 * Copyright 2026 Morphe.
 *
 * Injects document scripts via {@link WebContents#R0} ({@code WebContentsImpl} /
 * native {@code MseJ7A4a}), not {@link org.chromium.content_public.browser.RenderFrameHost#d}
 * which is WebAuth performGetAssertionWebAuthSecurityChecks.
 */

package app.morphe.extension.opera.patches;

import org.chromium.content_public.browser.WebContents;

import app.morphe.extension.shared.Logger;

public final class OperaPlaybackSpeedScriptInjector {
    private static final String VERIFY_LOG_TAG = "MorpheOperaPlaybackSpeed";

    private OperaPlaybackSpeedScriptInjector() {
    }

    /**
     * Runs {@code script} on the tab's {@link WebContents} after load completes.
     */
    public static void evaluate(WebContents webContents, String script) {
        if (webContents == null || script == null || script.isEmpty()) {
            return;
        }
        try {
            if (webContents.isDestroyed()) {
                return;
            }
            Logger.printInfo(() -> VERIFY_LOG_TAG + ": evaluating playback speed script (" + script.length() + " chars)");
            webContents.R0(script);
        } catch (Throwable throwable) {
            try {
                Logger.printException(() -> VERIFY_LOG_TAG + ": evaluate failed", throwable);
            } catch (Throwable suppressed) {
                // JNI may rethrow through JniAndroid.handleException; do not propagate.
            }
        }
    }
}