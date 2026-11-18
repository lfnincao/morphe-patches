package app.morphe.extension.mxplayerpro.delete;

import android.view.View;
import android.widget.CompoundButton;

import androidx.appcompat.widget.AppCompatCheckBox;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Delete shortcut for MX Player Pro customize items and toolbar.
 * Invoked via thin invoke-static hooks from Morphe patches (pattern from FTL-Patches ModSettings).
 */
public final class DeleteShortcutPatch {
    public static final int DELETE_SHORTCUT_BIT = 0x2000;
    public static final int DELETE_SHORTCUT_LIST_INDEX = 12;
    public static final int DELETE_SHORTCUT_VIEW_TYPE = 12;

    private static final int STRING_MENU_DELETE = 0x7f120475;
    private static final int DRAWABLE_DELETE = 0x7f080685;
    private static final String PREFS_KEY = "shortcuts_flag.3";
    private static final String PP0_CACHE_FIELD = "patchDeleteShortcutPp0";
    private static final String ACTIVITY_SCREEN_CLASS = "com.mxtech.videoplayer.ActivityScreen";

    private DeleteShortcutPatch() {}

    public static boolean bindCustomizeItem(Object adapter, Object holderBase, int position) {
        if (position != DELETE_SHORTCUT_LIST_INDEX) {
            return false;
        }
        try {
            AppCompatCheckBox check = findCheckbox(holderBase);
            if (check == null) {
                return false;
            }
            CharSequence label = check.getContext().getString(STRING_MENU_DELETE);
            check.setText(label);
            int flags = adapterFieldInt(adapter, "d");
            check.setChecked((flags & DELETE_SHORTCUT_BIT) != 0);
            Constructor<?> listenerCtor = Class.forName("n20").getConstructor(
                adapter.getClass(),
                int.class,
                holderBase.getClass()
            );
            CompoundButton.OnCheckedChangeListener listener =
                (CompoundButton.OnCheckedChangeListener) listenerCtor.newInstance(adapter, position, holderBase);
            check.setOnCheckedChangeListener(listener);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean onCustomizeToggle(Object listener, CompoundButton button) {
        try {
            int index = listenerFieldInt(listener, "d");
            if (index != DELETE_SHORTCUT_LIST_INDEX) {
                return false;
            }
            Object adapter = listenerField(listener, "k");
            boolean checked = button.isChecked();
            Method mutator = adapter.getClass().getMethod(checked ? "s" : "r", adapter.getClass(), int.class);
            mutator.invoke(null, adapter, DELETE_SHORTCUT_BIT);
            persistShortcutFlags(adapter);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static void ensureShortcutsPanelVisible(Object menuMoreFragment) {
        try {
            Field q = menuMoreFragment.getClass().getField("q");
            Object recycler = q.get(menuMoreFragment);
            if (recycler instanceof View) {
                ((View) recycler).setVisibility(View.VISIBLE);
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    public static int remapShortcutViewType(int viewType) {
        return viewType == DELETE_SHORTCUT_VIEW_TYPE ? 0 : viewType;
    }

    public static void syncToolbarShortcut(Object screen) {
        try {
            Class<?> v90 = Class.forName("V90");
            int e1 = v90.getField("e1").getInt(null);
            if ((e1 & DELETE_SHORTCUT_BIT) == 0) {
                removeCachedPp0(screen);
                return;
            }
            Object pp0 = getCachedPp0(screen);
            if (pp0 == null) {
                pp0 = createPp0(screen);
                setCachedPp0(screen, pp0);
            }
            Class<?> activityScreen = Class.forName(ACTIVITY_SCREEN_CLASS);
            Field listField = activityScreen.getField("A0");
            @SuppressWarnings("unchecked")
            ArrayList<Object> shortcuts = (ArrayList<Object>) listField.get(screen);
            if (!shortcuts.contains(pp0)) {
                shortcuts.add(pp0);
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    public static void deletePlayingFile(Object screen) {
        try {
            Class<?> activityScreen = Class.forName(ACTIVITY_SCREEN_CLASS);
            Class<?> inner = Class.forName(ACTIVITY_SCREEN_CLASS + "$w");
            Constructor<?> ctor = inner.getConstructor(activityScreen);
            ctor.newInstance(screen);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    public static boolean onToolbarShortcutClick(Object clickListener) {
        try {
            Object sp0g = listenerField(clickListener, "d");
            Object pp0 = field(sp0g, "I");
            if (pp0 == null) {
                return false;
            }
            int viewType = pp0FieldInt(pp0, "b");
            if (viewType != DELETE_SHORTCUT_VIEW_TYPE) {
                return false;
            }
            Object sp0 = field(sp0g, "N");
            Object screen = field(sp0, "d");
            deletePlayingFile(screen);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void persistShortcutFlags(Object adapter) throws ReflectiveOperationException {
        int flags = adapterFieldInt(adapter, "d");
        Class<?> lv = Class.forName("lV");
        Object m90 = lv.getField("A").get(null);
        Object prefs = field(m90, "d");
        Method edit = prefs.getClass().getMethod("edit");
        Object editor = edit.invoke(prefs);
        Method putInt = editor.getClass().getMethod("putInt", String.class, int.class);
        putInt.invoke(editor, PREFS_KEY, flags);
        Method commit = editor.getClass().getMethod("commit");
        commit.invoke(editor);
    }

    private static AppCompatCheckBox findCheckbox(Object holder) {
        for (Class<?> type = holder.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (AppCompatCheckBox.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(holder);
                        if (value instanceof AppCompatCheckBox) {
                            return (AppCompatCheckBox) value;
                        }
                    } catch (IllegalAccessException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static Object createPp0(Object screen) throws ReflectiveOperationException {
        Class<?> pp0 = Class.forName("Pp0");
        Constructor<?> ctor = pp0.getConstructor(int.class, int.class, boolean.class, boolean.class, int.class, String.class);
        Method getString = screen.getClass().getMethod("getString", int.class);
        String label = (String) getString.invoke(screen, STRING_MENU_DELETE);
        return ctor.newInstance(DELETE_SHORTCUT_VIEW_TYPE, 0, false, false, DRAWABLE_DELETE, label);
    }

    private static Object getCachedPp0(Object screen) throws ReflectiveOperationException {
        Class<?> activityScreen = Class.forName(ACTIVITY_SCREEN_CLASS);
        Field field = activityScreen.getDeclaredField(PP0_CACHE_FIELD);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static void setCachedPp0(Object screen, Object pp0) throws ReflectiveOperationException {
        Class<?> activityScreen = Class.forName(ACTIVITY_SCREEN_CLASS);
        Field field = activityScreen.getDeclaredField(PP0_CACHE_FIELD);
        field.setAccessible(true);
        field.set(screen, pp0);
    }

    private static void removeCachedPp0(Object screen) {
        try {
            Object pp0 = getCachedPp0(screen);
            if (pp0 == null) {
                return;
            }
            Class<?> activityScreen = Class.forName(ACTIVITY_SCREEN_CLASS);
            Field listField = activityScreen.getField("A0");
            @SuppressWarnings("unchecked")
            ArrayList<Object> shortcuts = (ArrayList<Object>) listField.get(screen);
            shortcuts.remove(pp0);
            setCachedPp0(screen, null);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    private static int adapterFieldInt(Object adapter, String name) throws ReflectiveOperationException {
        return adapter.getClass().getField(name).getInt(adapter);
    }

    private static int listenerFieldInt(Object listener, String name) throws ReflectiveOperationException {
        return listener.getClass().getField(name).getInt(listener);
    }

    private static Object listenerField(Object listener, String name) throws ReflectiveOperationException {
        return listener.getClass().getField(name).get(listener);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getField(name).get(target);
    }

    private static int pp0FieldInt(Object pp0, String name) throws ReflectiveOperationException {
        return pp0.getClass().getField(name).getInt(pp0);
    }
}