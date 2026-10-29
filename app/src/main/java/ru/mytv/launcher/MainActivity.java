package ru.mytv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // ---------- палитра ----------
    private static final int C_TEXT = 0xFFF1F3FA;
    private static final int C_MUTED = 0xFF8D97B5;
    private static final int C_TILE = 0xFF1B2340;
    private static final int C_TILE_FOCUS = 0xFF2A3560;
    private static final int C_ACCENT = 0xFF5B8CFF;
    private static final int C_VPN_ON_1 = 0xFF0FB57A, C_VPN_ON_2 = 0xFF0A7F86;
    private static final int C_VPN_OFF_1 = 0xFF3A3F5C, C_VPN_OFF_2 = 0xFF262A40;

    private static final int GRID_COLUMNS = 6;
    private static final String LEANBACK = "android.intent.category.LEANBACK_LAUNCHER";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Prefs prefs;

    private final Map<String, AppInfo> apps = new LinkedHashMap<>();
    private TextView clock, date, netStatus;
    private LinearLayout favRow;
    private GridLayout grid;
    private TextView gridTitle;
    private View vpnCard;
    private TextView vpnState, vpnSub;
    private boolean showHidden = false;

    // ============================================================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        setContentView(buildLayout());
        loadAppsAsync();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(pkgReceiver, pf);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tick.run();
        ui.post(statusLoop);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        ui.removeCallbacks(statusLoop);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        io.shutdownNow();
    }

    /** Кнопка «Назад» на главном экране ничего не делает — это домашний экран. */
    @Override
    public void onBackPressed() {
        View f = getCurrentFocus();
        if (vpnCard != null && f != vpnCard) vpnCard.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof AppInfo) {
                showAppMenu((AppInfo) f.getTag());
                return true;
            }
            if (f == vpnCard) { chooseVpnApp(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadAppsAsync(); }
    };

    // ============================================================
    // Разметка
    // ============================================================
    private View buildLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(36), dp(56), dp(48));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        // ---- шапка: часы + статус сети + настройки ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);

        LinearLayout timeBox = new LinearLayout(this);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        clock = text(52, C_TEXT, false);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date = text(18, C_MUTED, false);
        timeBox.addView(clock);
        timeBox.addView(date);
        header.addView(timeBox, new LinearLayout.LayoutParams(0, -2, 1));

        netStatus = text(16, C_MUTED, false);
        netStatus.setPadding(dp(16), dp(8), dp(16), dp(8));
        netStatus.setBackground(round(0x22FFFFFF, dp(20)));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.rightMargin = dp(20);
        header.addView(netStatus, nlp);

        header.addView(pillButton("⚙  Настройки", v -> openSettings()));
        root.addView(header);

        // ---- главный ряд: VPN + избранное ----
        root.addView(sectionTitle("Главное"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setPadding(dp(8), dp(14), dp(8), dp(14));
        favRow = new LinearLayout(this);
        favRow.setOrientation(LinearLayout.HORIZONTAL);
        favRow.setClipChildren(false);
        hs.addView(favRow);
        root.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        vpnCard = buildVpnCard();

        // ---- все приложения ----
        gridTitle = sectionTitle("Все приложения");
        root.addView(gridTitle);
        grid = new GridLayout(this);
        grid.setColumnCount(GRID_COLUMNS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(dp(8), dp(14), dp(8), dp(14));
        root.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = text(14, C_MUTED, false);
        hint.setText("Подсказка: удерживайте ОК или нажмите кнопку «Меню» на пульте — добавить в избранное, скрыть, переставить");
        hint.setPadding(dp(8), dp(12), 0, 0);
        root.addView(hint);

        return scroll;
    }

    private View buildVpnCard() {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(24), dp(20), dp(24), dp(20));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(26, Color.WHITE, true);
        title.setText("🛡  Happ VPN");
        vpnState = text(20, Color.WHITE, false);
        vpnSub = text(14, 0xCCFFFFFF, false);
        col.addView(title);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(14);
        col.addView(vpnState, lp);
        col.addView(vpnSub);
        card.addView(col);

        card.setOnClickListener(v -> openVpn());
        card.setOnLongClickListener(v -> { chooseVpnApp(); return true; });
        applyFocusEffect(card, true);
        updateVpnCard();
        return card;
    }

    // ============================================================
    // Загрузка приложений
    // ============================================================
    private void loadAppsAsync() {
        io.execute(() -> {
            final Map<String, AppInfo> found = queryApps();
            ui.post(() -> {
                apps.clear();
                apps.putAll(found);
                rebuildRows();
            });
        });
    }

    private Map<String, AppInfo> queryApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppInfo> out = new LinkedHashMap<>();
        String self = getPackageName();

        // Сначала ТВ-приложения (у них есть красивые баннеры), потом обычные
        for (String cat : new String[]{LEANBACK, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(cat);
            List<ResolveInfo> list;
            try { list = pm.queryIntentActivities(q, 0); } catch (Exception e) { continue; }
            for (ResolveInfo ri : list) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || self.equals(ai.packageName) || out.containsKey(ai.packageName)) continue;
                CharSequence l = ri.loadLabel(pm);
                AppInfo app = new AppInfo(l == null ? ai.packageName : l.toString(), ai.packageName,
                        new ComponentName(ai.packageName, ai.name), LEANBACK.equals(cat));
                try { app.banner = ai.loadBanner(pm); } catch (Exception ignored) { }
                if (app.banner == null) {
                    try { app.banner = ai.applicationInfo.loadBanner(pm); } catch (Exception ignored) { }
                }
                try { app.icon = ri.loadIcon(pm); } catch (Exception ignored) { }
                out.put(ai.packageName, app);
            }
        }
        // сортировка по названию
        List<AppInfo> sorted = new ArrayList<>(out.values());
        Collections.sort(sorted, new Comparator<AppInfo>() {
            @Override public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        Map<String, AppInfo> res = new LinkedHashMap<>();
        for (AppInfo a : sorted) res.put(a.pkg, a);
        return res;
    }

    // ============================================================
    // Построение рядов
    // ============================================================
    private void rebuildRows() {
        View focused = getCurrentFocus();
        String focusedPkg = focused != null && focused.getTag() instanceof AppInfo
                ? ((AppInfo) focused.getTag()).pkg : null;
        boolean vpnFocused = focused == vpnCard || focused == null;

        // ряд избранного
        favRow.removeAllViews();
        favRow.addView(vpnCard, new LinearLayout.LayoutParams(dp(340), dp(180)));
        ((LinearLayout.LayoutParams) vpnCard.getLayoutParams()).rightMargin = dp(22);
        List<String> favs = prefs.favorites();
        List<String> alive = new ArrayList<>();
        for (String p : favs) {
            AppInfo a = apps.get(p);
            if (a == null) continue;
            alive.add(p);
            View t = appTile(a, dp(300), dp(169));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(300), dp(169));
            lp.rightMargin = dp(22);
            lp.gravity = Gravity.CENTER_VERTICAL;
            favRow.addView(t, lp);
        }
        if (alive.size() != favs.size()) prefs.setFavorites(alive);
        if (alive.isEmpty()) {
            TextView empty = text(16, C_MUTED, false);
            empty.setText("Добавьте сюда любимые приложения:\nудерживайте ОК на приложении ниже");
            empty.setGravity(Gravity.CENTER_VERTICAL);
            favRow.addView(empty, new LinearLayout.LayoutParams(-2, dp(180)));
        }

        // сетка всех приложений
        grid.removeAllViews();
        Set<String> hidden = prefs.hidden();
        int shown = 0;
        int screenW = getResources().getDisplayMetrics().widthPixels - dp(56 * 2) - dp(16);
        int gap = dp(20);
        int tileW = (screenW - gap * GRID_COLUMNS) / GRID_COLUMNS;
        int tileH = tileW * 9 / 16;
        for (AppInfo a : apps.values()) {
            boolean isHidden = hidden.contains(a.pkg);
            if (isHidden && !showHidden) continue;
            View t = appTile(a, tileW, tileH);
            if (isHidden) t.setAlpha(0.45f);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = tileW;
            lp.height = tileH;
            lp.setMargins(0, 0, gap, gap);
            grid.addView(t, lp);
            shown++;
        }
        // плитка «показать скрытые»
        if (!hidden.isEmpty()) {
            View t = actionTile(showHidden ? "Спрятать скрытые" : "Скрытые (" + hidden.size() + ")",
                    v -> { showHidden = !showHidden; rebuildRows(); });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = tileW;
            lp.height = tileH;
            lp.setMargins(0, 0, gap, gap);
            grid.addView(t, lp);
        }
        gridTitle.setText("Все приложения · " + shown);

        // восстановить фокус
        View target = null;
        if (focusedPkg != null) target = findTileFor(focusedPkg);
        if (target == null && vpnFocused) target = vpnCard;
        if (target != null) target.requestFocus();
    }

    private View findTileFor(String pkg) {
        for (ViewGroup g : new ViewGroup[]{favRow, grid}) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View v = g.getChildAt(i);
                if (v.getTag() instanceof AppInfo && pkg.equals(((AppInfo) v.getTag()).pkg)) return v;
            }
        }
        return null;
    }

    private View appTile(final AppInfo a, int w, int h) {
        FrameLayout tile = new FrameLayout(this);
        tile.setTag(a);
        tile.setFocusable(true);
        tile.setClickable(true);

        if (a.banner != null) {
            ImageView img = new ImageView(this);
            img.setImageDrawable(a.banner);
            img.setScaleType(ImageView.ScaleType.FIT_XY);
            if (Build.VERSION.SDK_INT >= 21) img.setClipToOutline(true);
            tile.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setPadding(dp(10), dp(10), dp(10), dp(10));
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(a.icon);
            int is = Math.min(h / 2, dp(64));
            col.addView(ic, new LinearLayout.LayoutParams(is, is));
            TextView l = text(15, C_TEXT, false);
            l.setText(a.label);
            l.setSingleLine(true);
            l.setEllipsize(TextUtils.TruncateAt.END);
            l.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            col.addView(l, lp);
            tile.addView(col, new FrameLayout.LayoutParams(-1, -1));
        }

        tile.setOnClickListener(v -> launch(a));
        tile.setOnLongClickListener(v -> { showAppMenu(a); return true; });
        applyFocusEffect(tile, false);
        return tile;
    }

    private View actionTile(String label, View.OnClickListener l) {
        FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClickable(true);
        TextView t = text(16, C_MUTED, false);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        tile.addView(t, new FrameLayout.LayoutParams(-1, -1));
        tile.setOnClickListener(l);
        applyFocusEffect(tile, false);
        return tile;
    }

    // ============================================================
    // Фокус: плавное увеличение + рамка
    // ============================================================
    private void applyFocusEffect(final View v, final boolean isVpn) {
        refreshTileBg(v, false, isVpn);
        if (Build.VERSION.SDK_INT >= 21) v.setClipToOutline(true);
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .translationZ(has ? dp(12) : 0).setDuration(150).start();
            refreshTileBg(view, has, view == vpnCard);
        });
    }

    private void refreshTileBg(View v, boolean focused, boolean isVpn) {
        GradientDrawable g;
        if (isVpn) {
            boolean on = isVpnActive();
            g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    on ? new int[]{C_VPN_ON_1, C_VPN_ON_2} : new int[]{C_VPN_OFF_1, C_VPN_OFF_2});
        } else {
            g = new GradientDrawable();
            g.setColor(focused ? C_TILE_FOCUS : C_TILE);
        }
        g.setCornerRadius(dp(18));
        if (focused) g.setStroke(dp(3), Color.WHITE);
        v.setBackground(g);
    }

    // ============================================================
    // Действия
    // ============================================================
    private void launch(AppInfo a) {
        try {
            startActivity(a.launchIntent());
        } catch (Exception e) {
            Intent i = getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (i == null) i = getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
            if (i != null) {
                try { startActivity(i); return; } catch (Exception ignored) { }
            }
            toast("Не удалось открыть «" + a.label + "»");
        }
    }

    /** Ищем Happ: сначала выбранный вручную, потом по названию/пакету. */
    private AppInfo findVpnApp() {
        String manual = prefs.vpnPackage();
        if (manual != null && apps.containsKey(manual)) return apps.get(manual);
        AppInfo best = null;
        for (AppInfo a : apps.values()) {
            String l = a.label.toLowerCase(Locale.ROOT);
            String p = a.pkg.toLowerCase(Locale.ROOT);
            boolean match = l.startsWith("happ") || p.contains(".happ") || p.startsWith("happ")
                    || p.contains("happproxy");
            if (!match) continue;
            // Happ Plus — в приоритете
            if (l.contains("plus") || p.contains("plus")) return a;
            if (best == null) best = a;
        }
        return best;
    }

    private void openVpn() {
        AppInfo vpn = findVpnApp();
        if (vpn != null) {
            launch(vpn);
        } else {
            new AlertDialog.Builder(this, R.style.Dialog)
                    .setTitle("Happ не найден")
                    .setMessage("Установите Happ (или Happ Plus) для Android TV. "
                            + "Если он уже стоит, но называется иначе — выберите его вручную.")
                    .setPositiveButton("Выбрать вручную", (d, w) -> chooseVpnApp())
                    .setNegativeButton("Закрыть", null)
                    .show();
        }
    }

    private void chooseVpnApp() {
        final List<AppInfo> list = new ArrayList<>(apps.values());
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) names[i] = list.get(i).label;
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle("Какое приложение открывать кнопкой VPN?")
                .setItems(names, (d, which) -> {
                    prefs.setVpnPackage(list.get(which).pkg);
                    updateVpnCard();
                    toast("Кнопка VPN → " + list.get(which).label);
                })
                .show();
    }

    private void showAppMenu(final AppInfo a) {
        final boolean fav = prefs.isFavorite(a.pkg);
        final boolean hid = prefs.hidden().contains(a.pkg);
        final List<String> items = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();

        items.add("▶  Открыть");
        acts.add(() -> launch(a));
        items.add(fav ? "★  Убрать из избранного" : "☆  В избранное");
        acts.add(() -> { prefs.toggleFavorite(a.pkg); rebuildRows(); });
        if (fav) {
            items.add("←  Сдвинуть влево");
            acts.add(() -> { prefs.moveFavorite(a.pkg, -1); rebuildRows(); });
            items.add("→  Сдвинуть вправо");
            acts.add(() -> { prefs.moveFavorite(a.pkg, +1); rebuildRows(); });
        }
        items.add(hid ? "👁  Показать снова" : "🚫  Скрыть с экрана");
        acts.add(() -> { prefs.setHidden(a.pkg, !hid); rebuildRows(); });
        items.add("🛡  Сделать кнопкой VPN");
        acts.add(() -> { prefs.setVpnPackage(a.pkg); updateVpnCard(); toast("Кнопка VPN → " + a.label); });
        items.add("ℹ  О приложении / Остановить / Удалить");
        acts.add(() -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) { toast("На этом ТВ нет такого экрана"); }
        });

        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(a.label)
                .setItems(items.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("Не удалось открыть настройки");
        }
    }

    // ============================================================
    // Часы, сеть, VPN
    // ============================================================
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            Locale ru = new Locale("ru");
            clock.setText(new SimpleDateFormat("HH:mm", ru).format(now));
            String d = new SimpleDateFormat("EEEE, d MMMM", ru).format(now);
            date.setText(d.substring(0, 1).toUpperCase(ru) + d.substring(1));
            ui.postDelayed(this, 1000 * (60 - (System.currentTimeMillis() / 1000) % 60));
        }
    };

    private final Runnable statusLoop = new Runnable() {
        @Override public void run() {
            updateNetStatus();
            updateVpnCard();
            ui.postDelayed(this, 3000);
        }
    };

    private boolean isVpnActive() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void updateVpnCard() {
        if (vpnState == null) return;
        boolean on = isVpnActive();
        vpnState.setText(on ? "● Включён" : "○ Выключен");
        AppInfo vpn = apps.isEmpty() ? null : findVpnApp();
        if (apps.isEmpty()) vpnSub.setText("");
        else if (vpn == null) vpnSub.setText("Happ не установлен — нажмите ОК");
        else vpnSub.setText(on ? "Нажмите ОК, чтобы открыть " + vpn.label
                : "Нажмите ОК → в " + vpn.label + " нажмите «Подключить»");
        if (vpnCard != null) refreshTileBg(vpnCard, vpnCard.hasFocus(), true);
    }

    @SuppressWarnings("deprecation")
    private void updateNetStatus() {
        String s = "Нет сети";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                switch (ni.getType()) {
                    case ConnectivityManager.TYPE_WIFI: s = "Wi-Fi"; break;
                    case ConnectivityManager.TYPE_ETHERNET: s = "Кабель"; break;
                    case ConnectivityManager.TYPE_VPN: s = "VPN"; break;
                    default: s = "Онлайн";
                }
            }
        } catch (Exception ignored) { }
        netStatus.setText("●  " + s);
        netStatus.setTextColor("Нет сети".equals(s) ? 0xFFFF6B6B : 0xFF63E6BE);
    }

    // ============================================================
    // Утилиты
    // ============================================================
    private TextView sectionTitle(String s) {
        TextView t = text(20, C_MUTED, true);
        t.setText(s);
        t.setAllCaps(false);
        t.setPadding(dp(8), dp(26), 0, 0);
        return t;
    }

    private TextView pillButton(String label, View.OnClickListener l) {
        final TextView b = text(16, C_TEXT, false);
        b.setText(label);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(20), dp(10), dp(20), dp(10));
        b.setBackground(round(0x22FFFFFF, dp(22)));
        b.setOnClickListener(l);
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackground(round(has ? C_ACCENT : 0x22FFFFFF, dp(22)));
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(150).start();
        });
        return b;
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
