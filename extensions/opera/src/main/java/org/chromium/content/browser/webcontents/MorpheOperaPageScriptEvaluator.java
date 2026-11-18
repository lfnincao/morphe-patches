/*
 * Copyright 2026 Morphe.
 *
 * Lives in {@code org.chromium.content.browser.webcontents} so package-private
 * {@link WebContentsImpl#getNativePointer()} is legally callable at runtime (unlike
 * {@code app.morphe.extension.opera.patches.OperaPlaybackSpeedScriptInjector}).
 */

package org.chromium.content.browser.webcontents;

import org.chromium.content_public.browser.WebContents;

import J.N;

/**
 * Stock page-eval prologue ({@code Lmqi;->a}): {@code e()} → {@code b:J} → {@code MevqfbP8}.
 */
public final class MorpheOperaPageScriptEvaluator {
    private MorpheOperaPageScriptEvaluator() {
    }

  public static boolean evaluateScript(WebContents webContents, String script) {
        if (webContents == null || script == null || script.isEmpty()) {
            return false;
        }
        if (webContents.isDestroyed()) {
            return false;
        }
        if (!(webContents instanceof WebContentsImpl)) {
            return false;
        }
        WebContentsImpl impl = (WebContentsImpl) webContents;
        impl.e();
        long nativePtr = impl.getNativePointer();
        if (nativePtr == 0L) {
            return false;
        }
        N.MevqfbP8(nativePtr, script);
        return true;
    }
}