/*
 * Copyright 2026 Morphe.
 *
 * Page script evaluation follows stock Opera's only {@code MevqfbP8} call site
 * ({@code Lmqi;->a}, "process text" selection handler):
 *
 * <pre>
 *   invoke-virtual {WebContentsImpl}, WebContentsImpl;->e()V
 *   iget-wide v0, WebContentsImpl;->b:J
 *   invoke-static {v0, v1, script}, J/N;->MevqfbP8(JLjava/lang/Object;)V
 * </pre>
 *
 * {@link org.chromium.content_public.browser.WebContents#R0} routes to
 * {@code J.N.MseJ7A4a} (AddMessageToDevToolsConsole) — it does not run JS in the page.
 * {@code J.N.Mhlly_d7} is for {@code CaptureDevicesDialogDelegate} only; wrong pointer → SIGSEGV.
 */

package app.morphe.extension.opera.patches;

import org.chromium.content.browser.webcontents.WebContentsImpl;
import org.chromium.content_public.browser.WebContents;

import J.N;
import app.morphe.extension.opera.settings.OperaPlaybackSpeedPreferences;
import app.morphe.extension.shared.Logger;

public final class OperaPlaybackSpeedScriptInjector {
    private static final String VERIFY_LOG_TAG = "MorpheOperaPlaybackSpeed";

  private static volatile WebContents lastActiveWebContents;

    private OperaPlaybackSpeedScriptInjector() {
    }

    /**
     * Evaluates {@code script} in the tab's page context (main world), stock-equivalent to
     * {@code MevqfbP8}.
     */
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
            if (!(webContents instanceof WebContentsImpl)) {
                Logger.printInfo(() -> VERIFY_LOG_TAG + ": WebContents is not WebContentsImpl");
                disableInjectionSafely();
                return;
            }
            WebContentsImpl impl = (WebContentsImpl) webContents;
            impl.e();
            long nativePtr = impl.getNativePointer();
            if (nativePtr == 0L) {
                Logger.printInfo(() -> VERIFY_LOG_TAG + ": native WebContents pointer is zero");
                disableInjectionSafely();
                return;
            }
            Logger.printInfo(() -> VERIFY_LOG_TAG + ": MevqfbP8 (" + script.length() + " chars)");
            N.MevqfbP8(nativePtr, script);
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

    /** Re-runs injection on the last tab after the user changes the default speed in Settings. */
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
            // Best effort: never throw from injector after a failure.
        }
    }
}