package org.chromium.content_public.browser;

import org.chromium.base.Callback;
import org.chromium.url.Origin;

/** Compile-only stub for {@code RenderFrameHost}. */
public interface RenderFrameHost {
    /** Last committed origin ({@code c()} in Opera 103.3 dex). */
    Origin c();

    /**
     * Frame evaluateJavaScript without user gesture → native {@code J.N.M5dgGNo$}.
     */
    void d(String script, Origin origin, Callback<String> callback);

    /**
     * Frame evaluateJavaScript with user gesture → native {@code J.N.M2ouq_qG}.
     */
    void a(String script, Origin origin, boolean userGesture, Callback<String> callback);
}