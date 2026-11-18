/*
 * Copyright 2026 Morphe.
 *
 * Stock {@code WebContentsImpl.getNativePointer()} is {@code private} at runtime (Opera 103.3),
 * so same-package placement does not help. Uses reflection (setAccessible) on Opera's bundled
 * Chromium class, then {@code J.N.MevqfbP8(ptr, script)} — stock prologue {@code Lmqi;->a}.
 */

package org.chromium.content.browser.webcontents;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.chromium.content_public.browser.WebContents;

import J.N;

public final class MorpheOperaPageScriptEvaluator {
    private static final String NATIVE_PTR_METHOD = "getNativePointer";
    /** Stock Opera field read after {@code e()} ({@code iget-wide b:J}). */
    private static final String NATIVE_PTR_FIELD = "b";

    private static volatile Method cachedNativePointerMethod;
    private static volatile Field cachedNativePointerField;

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
        long nativePtr = nativeWebContentsPointer(impl);
        if (nativePtr == 0L) {
            return false;
        }
        N.MevqfbP8(nativePtr, script);
        return true;
    }

    private static long nativeWebContentsPointer(WebContentsImpl impl) {
        try {
            Method method = cachedNativePointerMethod;
            if (method == null) {
                synchronized (MorpheOperaPageScriptEvaluator.class) {
                    method = cachedNativePointerMethod;
                    if (method == null) {
                        method = resolveNativePointerMethod(impl);
                        cachedNativePointerMethod = method;
                    }
                }
            }
            if (method != null) {
                Object result = method.invoke(impl);
                if (result instanceof Long) {
                    return (Long) result;
                }
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to field read.
        }
        try {
            Field field = cachedNativePointerField;
            if (field == null) {
                synchronized (MorpheOperaPageScriptEvaluator.class) {
                    field = cachedNativePointerField;
                    if (field == null) {
                        field = resolveNativePointerField(impl);
                        cachedNativePointerField = field;
                    }
                }
            }
            if (field != null) {
                return field.getLong(impl);
            }
        } catch (ReflectiveOperationException ignored) {
            // Unable to resolve pointer.
        }
        return 0L;
    }

    private static Method resolveNativePointerMethod(WebContentsImpl impl) throws NoSuchMethodException {
        Class<?> type = impl.getClass();
        try {
            Method method = type.getDeclaredMethod(NATIVE_PTR_METHOD);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ex) {
            Method method = WebContentsImpl.class.getDeclaredMethod(NATIVE_PTR_METHOD);
            method.setAccessible(true);
            return method;
        }
    }

    private static Field resolveNativePointerField(WebContentsImpl impl) throws NoSuchFieldException {
        Class<?> type = impl.getClass();
        try {
            Field field = type.getDeclaredField(NATIVE_PTR_FIELD);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ex) {
            Field field = WebContentsImpl.class.getDeclaredField(NATIVE_PTR_FIELD);
            field.setAccessible(true);
            return field;
        }
    }
}