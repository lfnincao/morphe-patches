package org.chromium.content_public.browser;

/** Compile-only stub for Opera 103.3 {@code org.chromium.content_public.browser.WebContents}. */
@SuppressWarnings("unused")
public interface WebContents {
    /** DevTools console only ({@code MseJ7A4a}) — not page JS. */
    void R0(String script);

    boolean isDestroyed();

    /** Stock main frame getter (obfuscated name on 103.3). */
    RenderFrameHost F();
}