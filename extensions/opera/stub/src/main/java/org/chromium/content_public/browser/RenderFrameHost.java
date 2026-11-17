package org.chromium.content_public.browser;

import org.chromium.base.Callback;
import org.chromium.url.Origin;

/** Compile-only stub matching Opera 103.3 obfuscated API. */
@SuppressWarnings("unused")
public interface RenderFrameHost {
    Origin c();

    void d(String script, Origin origin, Callback<?> callback);
}