package app.morphe.extension.mxplayerfree.delete;

import android.app.Activity;
import android.content.SharedPreferences;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Delete action for MX Player free 3.5.3 Customize items ({@code Luad} /
 * {@code MenuHelper.b()}).
 */
public final class DeleteShortcutPatch {
    /** Stable {@code Lcbd$b.a} id (unused in stock 3.5.3). */
    public static final int DELETE_MENU_TYPE = 22;
    public static final String DELETE_MENU_PREF_KEY = "key_show_delete_file";

    private static final String ACTIVITY_SCREEN_CLASS = "com.mxtech.videoplayer.ActivityScreen";
    private static final String MENU_ITEM_CLASS = "cbd$b";
    private static final String MENU_SECTION_CLASS = "cbd$a";

    private DeleteShortcutPatch() {}

    /** Appends Delete to the six-item customize picker list ({@code Luad$a} uses {@code cbd.b()}). */
    public static void appendDeleteCustomizeCandidate(Object menuHelper, ArrayList<Object> list) {
        try {
            list.add(createMenuItem(menuHelper, false));
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    /** Registers portrait/landscape slot maps after stock {@code MenuHelper.d()}. */
    public static void registerDeleteMenuEntry(Object menuHelper) {
        try {
            Class<?> cbd = menuHelper.getClass();
            Object portrait = cbd.getField("h").get(menuHelper);
            Object landscape = cbd.getField("i").get(menuHelper);
            if (sectionHasType(portrait, DELETE_MENU_TYPE) || sectionHasType(landscape, DELETE_MENU_TYPE)) {
                return;
            }
            SharedPreferences prefs = prefs();
            int packed = prefs.getInt(DELETE_MENU_PREF_KEY, -1);
            if (packed < 0) {
                return;
            }
            int portraitSlot = packed % 10;
            int landscapeSlot = packed / 10;
            Object portraitItem = createMenuItem(menuHelper, true);
            Object landscapeItem = createMenuItem(menuHelper, true);
            appendToSection(portrait, portraitItem, portraitSlot);
            appendToSection(landscape, landscapeItem, landscapeSlot);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    /**
     * Luad customize rows toggle enabled slots; never delete media from the settings picker.
     */
    public static boolean onCustomizeRowClick(Object clickListener) {
        return false;
    }

    /** Intercepts {@code Ljy1;->a(I)} when the player customize control fires delete. */
    /**
     * Luad Apply ({@code Lb3.onClick}) persists the six stock top-bar slots only; this mirrors that
     * path for delete (type {@link #DELETE_MENU_TYPE}) via {@code Lcbd.a(I)} encoding.
     */
    public static void persistDeleteTopBarMenuPref(Object applyListener) {
        try {
            Object luad = field(applyListener, "c");
            Object cbd = field(luad, "j0");
            Method encode = cbd.getClass().getMethod("a", int.class);
            int packed = (Integer) encode.invoke(cbd, DELETE_MENU_TYPE);
            prefs().edit().putInt(DELETE_MENU_PREF_KEY, packed).apply();
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    public static boolean onCustomizeMenuAction(Object jy1, int menuType) {
        if (menuType != DELETE_MENU_TYPE) {
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
            // no-op when player is not on ActivityScreen
        }
    }

    private static boolean sectionHasType(Object section, int type) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        ArrayList<Object> list = (ArrayList<Object>) field(section, "a");
        for (Object entry : list) {
            if (entry.getClass().getField("a").getInt(entry) == type) {
                return true;
            }
        }
        return false;
    }

    private static void appendToSection(Object section, Object item, int slot) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        ArrayList<Object> list = (ArrayList<Object>) field(section, "a");
        @SuppressWarnings("unchecked")
        HashMap<Integer, Object> map = (HashMap<Integer, Object>) field(section, "b");
        list.add(item);
        map.put(slot, item);
    }

    private static Object createMenuItem(Object menuHelper, boolean includeMoreIcon)
        throws ReflectiveOperationException {
        Activity activity = (Activity) field(menuHelper, "a");
        int icon = activity.getResources().getIdentifier("ic_more_delete_white", "drawable", activity.getPackageName());
        if (icon == 0) {
            icon = activity.getResources().getIdentifier("ic_more_delete", "drawable", activity.getPackageName());
        }
        if (icon == 0) {
            icon = activity.getResources().getIdentifier("mxskin__smb_delete_ic__light", "drawable", activity.getPackageName());
        }
        int labelRes = activity.getResources().getIdentifier("delete_file_item", "string", activity.getPackageName());
        if (labelRes == 0) {
            labelRes = activity.getResources().getIdentifier("menu_delete", "string", activity.getPackageName());
        }
        String label = labelRes != 0 ? activity.getString(labelRes) : "Delete";
        Class<?> itemClass = Class.forName(MENU_ITEM_CLASS);
        if (includeMoreIcon) {
            int iconMore = activity.getResources().getIdentifier("ic_more_delete", "drawable", activity.getPackageName());
            if (iconMore == 0) {
                iconMore = icon;
            }
            Constructor<?> ctor = itemClass.getConstructor(int.class, int.class, int.class, String.class);
            return ctor.newInstance(DELETE_MENU_TYPE, icon, iconMore, label);
        }
        Constructor<?> ctor = itemClass.getConstructor(int.class, int.class, String.class);
        return ctor.newInstance(DELETE_MENU_TYPE, icon, label);
    }

    private static SharedPreferences prefs() throws ReflectiveOperationException {
        Class<?> zjc = Class.forName("zjc");
        Method b = Class.forName("igg").getMethod("b");
        return (SharedPreferences) b.invoke(null);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getField(name).get(target);
    }
}