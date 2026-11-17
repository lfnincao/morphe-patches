package J;

/**
 * Minimal compile-time stub for Opera's obfuscated Chromium JNI class.
 * At runtime this type is provided by the browser APK (classes4.dex).
 */
@SuppressWarnings("unused")
public final class N {
    private N() {
    }

    /**
     * {@code WebContentsAndroid::EvaluateJavaScript} — runs script in the page main world.
     *
     * @param startRenderer when false, matches legacy ContentViewCore evaluateJavaScript.
     */
    public static native void Mhlly_d7(long webContentsPtr, Object script, Object callback, boolean startRenderer);
}