package ru.mytv.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Список приложений + распознавание «главных». */
final class AppRepo {
    private static final String LEANBACK = "android.intent.category.LEANBACK_LAUNCHER";

    /** Порядок плиток на главном экране и ключевые слова для поиска (название или пакет). */
    static final String[][] FEATURED = {
            {"кинопоиск", "kinopoisk"},
            {"youtube", "smarttube", "teamsmart"},
            {"яндекс браузер", "yandex browser", "yandex.browser", "browser.tv", "яндекс.браузер"},
            {"netflix"},
            {"apple tv", "com.apple.atve"},
            {"instagram"},
            {"itv"},
            {"rustore", "vk.store"},
    };

    /** Служебный мусор, который не показываем даже во «Всех приложениях». */
    private static final String[] JUNK = {
            "daemon", "plugin", "server", "mainactivity", "memmanager", "launcher"
    };

    static Map<String, AppInfo> query(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        String self = ctx.getPackageName();
        Map<String, AppInfo> out = new LinkedHashMap<>();
        for (String cat : new String[]{LEANBACK, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(cat);
            List<ResolveInfo> list;
            try { list = pm.queryIntentActivities(q, 0); } catch (Exception e) { continue; }
            for (ResolveInfo ri : list) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || self.equals(ai.packageName) || out.containsKey(ai.packageName)) continue;
                CharSequence l = ri.loadLabel(pm);
                AppInfo a = new AppInfo(l == null ? ai.packageName : l.toString().trim(), ai.packageName,
                        new ComponentName(ai.packageName, ai.name), LEANBACK.equals(cat));
                try { a.banner = ai.loadBanner(pm); } catch (Exception ignored) { }
                if (a.banner == null) {
                    try { a.banner = ai.applicationInfo.loadBanner(pm); } catch (Exception ignored) { }
                }
                try { a.icon = ri.loadIcon(pm); } catch (Exception ignored) { }
                a.color = dominantColor(a.banner != null ? a.banner : a.icon);
                out.put(a.pkg, a);
            }
        }
        List<AppInfo> sorted = new ArrayList<>(out.values());
        Collections.sort(sorted, new Comparator<AppInfo>() {
            @Override public int compare(AppInfo x, AppInfo y) { return x.label.compareToIgnoreCase(y.label); }
        });
        Map<String, AppInfo> res = new LinkedHashMap<>();
        for (AppInfo a : sorted) res.put(a.pkg, a);
        return res;
    }

    /** Кэш списка приложений (сбрасывается при установке/удалении). */
    static volatile Map<String, AppInfo> cache;

    static int featuredIndex(AppInfo a) {
        for (int i = 0; i < FEATURED.length; i++) if (matches(a, FEATURED[i])) return i;
        return -1;
    }

    static boolean onHome(AppInfo a, Prefs p) {
        if (p.pinned().contains(a.pkg)) return true;
        return featuredIndex(a) >= 0 && !p.unpinned().contains(a.pkg) && !p.hidden().contains(a.pkg);
    }

    static void setOnHome(AppInfo a, Prefs p, boolean on) {
        List<String> pin = p.pinned();
        if (on) {
            p.setFlag("unpinned", a.pkg, false);
            p.setFlag("hidden", a.pkg, false);
            if (featuredIndex(a) < 0 && !pin.contains(a.pkg)) pin.add(a.pkg);
        } else {
            pin.remove(a.pkg);
            if (featuredIndex(a) >= 0) p.setFlag("unpinned", a.pkg, true);
        }
        p.setPinned(pin);
    }

    /** Плитки главного экрана: сначала «главные» по порядку, потом добавленные вручную. */
    static List<AppInfo> homeApps(Map<String, AppInfo> apps, Prefs p, String vpnPkg) {
        List<AppInfo> out = new ArrayList<>();
        for (String[] keys : FEATURED) {
            for (AppInfo a : apps.values()) {
                if (a.pkg.equals(vpnPkg) || out.contains(a)) continue;
                if (matches(a, keys) && onHome(a, p)) out.add(a);
            }
        }
        for (String pkg : p.pinned()) {
            AppInfo a = apps.get(pkg);
            if (a != null && !out.contains(a) && !pkg.equals(vpnPkg)) out.add(a);
        }
        return out;
    }

    static boolean matches(AppInfo a, String[] keys) {
        String l = a.label.toLowerCase(Locale.ROOT);
        String p = a.pkg.toLowerCase(Locale.ROOT);
        for (String k : keys) {
            if (k.equals("itv")) { if (l.equals("itv")) return true; continue; }
            if (l.contains(k) || p.contains(k)) return true;
        }
        return false;
    }

    static boolean isJunk(AppInfo a) {
        String l = a.label.toLowerCase(Locale.ROOT);
        for (String j : JUNK) if (l.contains(j)) return true;
        return false;
    }

    /** Happ / Happ Plus. */
    static AppInfo findVpn(Map<String, AppInfo> apps, String manual) {
        if (manual != null && apps.containsKey(manual)) return apps.get(manual);
        AppInfo best = null;
        for (AppInfo a : apps.values()) {
            String l = a.label.toLowerCase(Locale.ROOT);
            String p = a.pkg.toLowerCase(Locale.ROOT);
            if (!(l.startsWith("happ") || p.contains(".happ") || p.startsWith("happ") || p.contains("happproxy"))) continue;
            if (l.contains("plus") || p.contains("plus")) return a;
            if (best == null) best = a;
        }
        return best;
    }

    /** Фирменный цвет иконки: самый «многочисленный» насыщенный оттенок. */
    static int dominantColor(Drawable d) {
        if (d == null) return 0xFF2C2C2E;
        try {
            int s = 32;
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.draw(c);
            int[] px = new int[s * s];
            b.getPixels(px, 0, s, 0, 0, s, s);
            b.recycle();
            int[] cnt = new int[12];
            long[][] sum = new long[12][3];
            float[] hsv = new float[3];
            for (int p : px) {
                if (Color.alpha(p) < 200) continue;
                Color.colorToHSV(p, hsv);
                if (hsv[1] < 0.35f || hsv[2] < 0.3f) continue;
                int k = Math.min(11, (int) (hsv[0] / 30f));
                cnt[k]++;
                sum[k][0] += Color.red(p);
                sum[k][1] += Color.green(p);
                sum[k][2] += Color.blue(p);
            }
            int best = -1;
            for (int i = 0; i < 12; i++) if (best < 0 || cnt[i] > cnt[best]) best = i;
            if (best < 0 || cnt[best] < 12) return 0xFF2C2C2E; // бесцветная иконка → графит
            int n = cnt[best];
            return Color.rgb((int) (sum[best][0] / n), (int) (sum[best][1] / n), (int) (sum[best][2] / n));
        } catch (Exception e) {
            return 0xFF2C2C2E;
        }
    }
}
