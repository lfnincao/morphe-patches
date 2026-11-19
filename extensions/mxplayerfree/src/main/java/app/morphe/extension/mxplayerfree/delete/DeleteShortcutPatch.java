package app.morphe.extension.mxplayerfree.delete;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Delete shortcut for MX Player free 3.5.3: Shortcuts list toggle + toolbar placement;
 * delete action uses stock {@link #ACTIVITY_SCREEN_CLASS}$y confirm flow.
 */
public final class DeleteShortcutPatch {
    public static final int DELETE_SHORTCUT_BIT = 0x10000;
    /** Distinct from stock toolbar types (11=customize, 14–17=actions) on free 3.5.3. */
    public static final int DELETE_SHORTCUT_VIEW_TYPE = 18;

    private static final String DELETE_SHORTCUT_KEY = "delete_file";
    private static final String CACHE_FIELD = "patchDeleteShortcutB0j";
    private static final String ACTIVITY_SCREEN_CLASS = "com.mxtech.videoplayer.ActivityScreen";
    private static final String SHORTCUTS_LIST_FIELD = "b0";

    private DeleteShortcutPatch() {}

    /** Reconcile checkbox with flag bit after stock row bind (label + listener). */
    public static void overlayDeleteRowChecked(Object adapter, Object viewHolder, int position) {
        try {
            Field listField = adapter.getClass().getField("i");
            @SuppressWarnings("unchecked")
            ArrayList<String> keys = (ArrayList<String>) listField.get(adapter);
            if (position < 0 || position >= keys.size()) {
                return;
            }
            if (!DELETE_SHORTCUT_KEY.equals(keys.get(position))) {
                return;
            }
            int flags = adapter.getClass().getField("j").getInt(adapter);
            Object checkbox = viewHolder.getClass().getField("b").get(viewHolder);
            if (checkbox instanceof android.widget.CompoundButton) {
                ((android.widget.CompoundButton) checkbox).setChecked((flags & DELETE_SHORTCUT_BIT) != 0);
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    public static void toggleDeleteShortcut(Object adapter, boolean checked) {
        try {
            Field flagsField = adapter.getClass().getField("j");
            int flags = flagsField.getInt(adapter);
            if (checked) {
                flags |= DELETE_SHORTCUT_BIT;
            } else {
                flags &= ~DELETE_SHORTCUT_BIT;
            }
            flagsField.setInt(adapter, flags);
            persistShortcutsFlag3(flags);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    public static int remapShortcutViewType(int viewType) {
        return viewType == DELETE_SHORTCUT_VIEW_TYPE ? 0 : viewType;
    }

    public static void ensureShortcutsPanelVisible(Object menuMoreFragment) {
        try {
            Field recyclerField = menuMoreFragment.getClass().getField("O");
            Object recycler = recyclerField.get(menuMoreFragment);
            if (recycler instanceof View) {
                ((View) recycler).setVisibility(View.VISIBLE);
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    /**
     * Hooked inside {@code ActivityScreen.Ya()} before {@code Collections.sort} so delete uses the
     * same {@code Xa} placement path as stock shortcuts instead of a post-build list append.
     */
    public static void rebuildDeleteShortcutInYa(Object screen) {
        try {
            Class<?> flagsClass = Class.forName("b6f");
            int packed = flagsClass.getField("f1").getInt(null);
            if ((packed & DELETE_SHORTCUT_BIT) == 0) {
                removeCachedToolbarShortcut(screen);
                return;
            }
            Class<?> activityScreen = Class.forName(ACTIVITY_SCREEN_CLASS);
            Object shortcut = getCachedToolbarShortcut(screen);
            if (shortcut == null) {
                shortcut = createToolbarShortcut(screen);
                setCachedToolbarShortcut(screen, shortcut);
            }
            Field listField = activityScreen.getField(SHORTCUTS_LIST_FIELD);
            @SuppressWarnings("unchecked")
            ArrayList<Object> shortcuts = (ArrayList<Object>) listField.get(screen);
            shortcuts.remove(shortcut);
            Method place = activityScreen.getMethod("Xa", Class.forName("b0j"), boolean.class, boolean.class);
            place.invoke(screen, shortcut, Boolean.FALSE, Boolean.FALSE);
            if (!shortcuts.contains(shortcut)) {
                shortcuts.add(shortcut);
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

  /** Intercepts {@code Ljy1;->a(I)} when the player customize control fires delete (type 18). */
    public static boolean onCustomizeMenuAction(Object jy1, int menuType) {
        if (menuType != DELETE_SHORTCUT_VIEW_TYPE) {
            return false;
        }
        try {
            Object screen = resolveActivityScreenFromMenuBridge(jy1);
            if (screen == null) {
                return false;
            }
            deletePlayingFile(screen);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    /**
     * {@code Ljy1;} holds menu UI in synthetic field {@code b} ({@code Lrbd;}/{@code Ly9d;});
     * {@link #ACTIVITY_SCREEN_CLASS} is on {@code Ly9d;.L}, not on {@code Ljy1;} itself.
     */
    private static Object resolveActivityScreenFromMenuBridge(Object bridge) throws ReflectiveOperationException {
        Object menu = field(bridge, "b");
        return menu.getClass().getField("L").get(menu);
    }

    public static void deletePlayingFile(Object screen) {
        try {
            Class<?> activityScreen = Class.forName(ACTIVITY_SCREEN_CLASS);
            Class<?> inner = Class.forName(ACTIVITY_SCREEN_CLASS + "$y");
            Constructor<?> ctor = inner.getConstructor(activityScreen);
            ctor.newInstance(screen);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    public static boolean onToolbarShortcutClick(Object clickListener) {
        try {
            Object clickGroup = listenerField(clickListener, "b");
            Object shortcut = field(clickGroup, "b");
            if (shortcut == null) {
                return false;
            }
            int viewType = shortcut.getClass().getField("b").getInt(shortcut);
            if (viewType != DELETE_SHORTCUT_VIEW_TYPE) {
                return false;
            }
            Object widgetAdapter = field(clickGroup, "g");
            Object screen = field(widgetAdapter, "j");
            if (screen == null) {
                return false;
            }
            deletePlayingFile(screen);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Object createToolbarShortcut(Object screen) throws ReflectiveOperationException {
        Class<?> model = Class.forName("b0j");
        Constructor<?> ctor = model.getConstructor(
            int.class,
            int.class,
            String.class,
            boolean.class,
            boolean.class,
            int.class
        );
        Context context = (Context) screen;
        int labelId = context.getResources().getIdentifier("menu_delete", "string", context.getPackageName());
        String label = labelId != 0 ? context.getString(labelId) : "Delete";
        int iconId = context.getResources().getIdentifier("mxskin__smb_delete_ic__light", "drawable", context.getPackageName());
        if (iconId == 0) {
            iconId = context.getResources().getIdentifier("mxskin__smb_delete_ic__dark", "drawable", context.getPackageName());
        }
        if (iconId == 0) {
            iconId = context.getResources().getIdentifier("ic_delete", "drawable", context.getPackageName());
        }
        return ctor.newInstance(DELETE_SHORTCUT_VIEW_TYPE, 0, label, false, false, iconId);
    }

    private static Object getCachedToolbarShortcut(Object screen) throws ReflectiveOperationException {
        Field field = Class.forName(ACTIVITY_SCREEN_CLASS).getDeclaredField(CACHE_FIELD);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static void setCachedToolbarShortcut(Object screen, Object shortcut) throws ReflectiveOperationException {
        Field field = Class.forName(ACTIVITY_SCREEN_CLASS).getDeclaredField(CACHE_FIELD);
        field.setAccessible(true);
        field.set(screen, shortcut);
    }

    private static void removeCachedToolbarShortcut(Object screen) {
        try {
            Object shortcut = getCachedToolbarShortcut(screen);
            if (shortcut == null) {
                return;
            }
            Field listField = Class.forName(ACTIVITY_SCREEN_CLASS).getField(SHORTCUTS_LIST_FIELD);
            @SuppressWarnings("unchecked")
            ArrayList<Object> shortcuts = (ArrayList<Object>) listField.get(screen);
            shortcuts.remove(shortcut);
            setCachedToolbarShortcut(screen, null);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    private static void persistShortcutsFlag3(int flags) throws ReflectiveOperationException {
        Class<?> application = Class.forName("zjc");
        Field prefsHolder = application.getField("o");
        Object holder = prefsHolder.get(null);
        Field editorField = holder.getClass().getField("b");
        Object preferences = editorField.get(holder);
        Method edit = preferences.getClass().getMethod("edit");
        Object editor = edit.invoke(preferences);
        Method putInt = editor.getClass().getMethod("putInt", String.class, int.class);
        putInt.invoke(editor, "shortcuts_flag.3", flags);
        Method commit = editor.getClass().getMethod("commit");
        commit.invoke(editor);
    }

    private static Object listenerField(Object listener, String name) throws ReflectiveOperationException {
        return listener.getClass().getField(name).get(listener);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getField(name).get(target);
    }
}