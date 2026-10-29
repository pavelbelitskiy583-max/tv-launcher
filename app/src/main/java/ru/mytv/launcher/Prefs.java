package ru.mytv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Что стоит на главном экране, что скрыто. */
final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) { sp = c.getSharedPreferences("launcher2", Context.MODE_PRIVATE); }

    /** Добавленные вручную на главный экран (по порядку). */
    List<String> pinned() {
        String s = sp.getString("pinned", "");
        List<String> out = new ArrayList<>();
        if (!TextUtils.isEmpty(s)) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    void setPinned(List<String> l) { sp.edit().putString("pinned", TextUtils.join(",", l)).apply(); }

    /** Убранные с главного экрана, даже если они из списка «главных». */
    Set<String> unpinned() { return set("unpinned"); }

    Set<String> hidden() { return set("hidden"); }

    void setFlag(String key, String pkg, boolean on) {
        Set<String> s = set(key);
        if (on) s.add(pkg); else s.remove(pkg);
        sp.edit().putStringSet(key, s).apply();
    }

    String vpnPackage() { return sp.getString("vpn_pkg", null); }

    void setVpnPackage(String p) { sp.edit().putString("vpn_pkg", p).apply(); }

    private Set<String> set(String key) {
        return new HashSet<>(sp.getStringSet(key, new HashSet<String>()));
    }
}
