package ru.mytv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Избранное (с порядком) и скрытые приложения. */
final class Prefs {
    private static final String FAV = "favorites";
    private static final String HIDDEN = "hidden";
    private static final String VPN_PKG = "vpn_pkg";
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    List<String> favorites() {
        String s = sp.getString(FAV, "");
        List<String> out = new ArrayList<>();
        if (!TextUtils.isEmpty(s)) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    void setFavorites(List<String> list) {
        sp.edit().putString(FAV, TextUtils.join(",", list)).apply();
    }

    boolean isFavorite(String pkg) { return favorites().contains(pkg); }

    void toggleFavorite(String pkg) {
        List<String> f = favorites();
        if (f.contains(pkg)) f.remove(pkg); else f.add(pkg);
        setFavorites(f);
    }

    void moveFavorite(String pkg, int delta) {
        List<String> f = favorites();
        int i = f.indexOf(pkg);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= f.size()) return;
        f.remove(i);
        f.add(j, pkg);
        setFavorites(f);
    }

    Set<String> hidden() {
        return new HashSet<>(sp.getStringSet(HIDDEN, new HashSet<String>()));
    }

    void setHidden(String pkg, boolean hide) {
        Set<String> h = hidden();
        if (hide) h.add(pkg); else h.remove(pkg);
        sp.edit().putStringSet(HIDDEN, h).apply();
    }

    /** Пакет, выбранный вручную для кнопки VPN (если автоопределение не сработало). */
    String vpnPackage() { return sp.getString(VPN_PKG, null); }

    void setVpnPackage(String pkg) { sp.edit().putString(VPN_PKG, pkg).apply(); }
}
