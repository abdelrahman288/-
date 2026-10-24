package com.noor.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/** قائمة التطبيقات + المفضلة + صف "المزيد" (محفوظين في SharedPreferences). */
final class AppRepo {
    private AppRepo() {}

    static final int ICON = 96;
    static final String KEY_FAVS = "favs";
    static final String KEY_MORE = "more";

    static volatile List<AppItem> apps = new ArrayList<>();
    static Typeface UI = Typeface.DEFAULT;
    private static SharedPreferences sp;

    static void init(Context ctx) {
        sp = ctx.getSharedPreferences("noor", Context.MODE_PRIVATE);
    }

    static SharedPreferences prefs() {
        return sp;
    }

    /** يُستدعى من thread خلفي. */
    static void load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        LinkedHashMap<String, AppItem> map = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(cat);
            List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
            for (ResolveInfo ri : list) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null) continue;
                String pkg = ai.packageName;
                if (pkg.equals(ctx.getPackageName()) || map.containsKey(pkg)) continue;
                CharSequence l = ri.loadLabel(pm);
                String label = l == null ? pkg : l.toString();
                Bitmap bmp = toBitmap(ri.loadIcon(pm));
                map.put(pkg, new AppItem(pkg, label, new android.content.ComponentName(pkg, ai.name), bmp));
            }
        }
        ArrayList<AppItem> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        apps = out;
        ensureDefaults();
    }

    private static Bitmap toBitmap(Drawable d) {
        Bitmap b = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888);
        if (d == null) return b;
        Canvas c = new Canvas(b);
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            Bitmap src = ((BitmapDrawable) d).getBitmap();
            c.drawBitmap(Bitmap.createScaledBitmap(src, ICON, ICON, true), 0, 0, null);
        } else {
            d.setBounds(0, 0, ICON, ICON);
            d.draw(c);
        }
        return b;
    }

    static AppItem find(String pkg) {
        for (AppItem a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    // ---------------- القوائم ----------------

    static List<String> read(String key) {
        ArrayList<String> out = new ArrayList<>();
        String s = sp.getString(key, "");
        if (s == null || s.isEmpty()) return out;
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private static void write(String key, List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String p : l) {
            if (b.length() > 0) b.append(',');
            b.append(p);
        }
        sp.edit().putString(key, b.toString()).apply();
    }

    /** يرجّع التطبيقات المثبّتة فعلاً من القائمة المحفوظة. */
    static List<AppItem> resolve(String key) {
        ArrayList<AppItem> out = new ArrayList<>();
        for (String p : read(key)) {
            AppItem a = find(p);
            if (a != null) out.add(a);
        }
        return out;
    }

    static void addTo(String key, String pkg) {
        String other = key.equals(KEY_FAVS) ? KEY_MORE : KEY_FAVS;
        removeFrom(other, pkg);
        List<String> l = read(key);
        l.remove(pkg);
        l.add(pkg);
        int max = key.equals(KEY_FAVS) ? Config.MAX_FAVS : Config.MAX_MORE;
        while (l.size() > max) l.remove(0);
        write(key, l);
    }

    static void removeFrom(String key, String pkg) {
        List<String> l = read(key);
        if (l.remove(pkg)) write(key, l);
    }

    // ---------------- الافتراضيات ----------------

    private static final String[][] FAV_GROUPS = {
            {"youtube"}, {"shahid", "شاهد"}, {"netflix"}, {"prime video"}, {"disney"}, {"watch it", "watchit"},
            {"kodi"}, {"vlc"}, {"browse", "chrome", "puffin"}};
    private static final String[][] MORE_GROUPS = {
            {"play store", "متجر"}, {"media", "وسائط"}, {"file", "ملفات", "ملف"}, {"settings", "إعدادات"},
            {"youtube music", "يوتيوب ميوزك"}, {"spotify", "anghami"}};

    private static void ensureDefaults() {
        if (apps.isEmpty()) return;
        if (!sp.contains(KEY_FAVS)) write(KEY_FAVS, pick(FAV_GROUPS, Config.MAX_FAVS, new ArrayList<String>()));
        if (!sp.contains(KEY_MORE)) write(KEY_MORE, pick(MORE_GROUPS, Config.MAX_MORE, read(KEY_FAVS)));
    }

    private static List<String> pick(String[][] groups, int max, List<String> exclude) {
        ArrayList<String> out = new ArrayList<>();
        for (String[] g : groups) {
            if (out.size() >= max) break;
            for (AppItem a : apps) {
                if (exclude.contains(a.pkg) || out.contains(a.pkg)) continue;
                String l = a.label.toLowerCase();
                boolean hit = false;
                for (String kw : g) if (l.contains(kw)) hit = true;
                if (g[0].equals("youtube") && (l.contains("music") || l.contains("ميوزك"))) hit = false;
                if (hit) {
                    out.add(a.pkg);
                    break;
                }
            }
        }
        return out;
    }
}
