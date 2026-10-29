package ru.mytv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Главный экран в стиле Apple TV: размытый фон, верхний ряд, сетка ниже. */
public class MainActivity extends Activity {

    private static final int COLS = 5;
    private static final String TAG_VPN = "vpn", TAG_SETTINGS = "settings";

    // цвета системных иконок Apple
    private static final int BLUE_1 = 0xFF3D9BFF, BLUE_2 = 0xFF0040DD;
    private static final int GREEN_1 = 0xFF4CDB72, GREEN_2 = 0xFF1F8A3D;
    private static final int GRAY_1 = 0xFF9A9AA0, GRAY_2 = 0xFF48484A;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Prefs prefs;

    private ImageView bgA, bgB;
    private boolean bgFront = true;
    private Object bgTag;
    private ScrollView scroll;
    private LinearLayout dock;
    private GridLayout grid;
    private TextView clock;
    private View vpnDot, vpnCard;
    private Object focusedTag = TAG_VPN;
    private boolean showHidden = false;
    private boolean lastVpn = false;
    private int sw, sh, side, gap, tileW, tileH;

    // ------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        setContentView(buildLayout());

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
        if (AppRepo.cache == null) reload(); else rebuild();
        ui.post(clockTick);
        ui.post(statusLoop);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(clockTick);
        ui.removeCallbacks(statusLoop);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        io.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        scroll.smoothScrollTo(0, 0);
        if (vpnCard != null) vpnCard.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null) { showMenuFor(f.getTag()); return true; }
        }
        return super.onKeyDown(keyCode, e);
    }

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { AppRepo.cache = null; reload(); }
    };

    // ------------------------------------------------------------------
    private View buildLayout() {
        sw = getResources().getDisplayMetrics().widthPixels;
        sh = getResources().getDisplayMetrics().heightPixels;
        side = (int) (sw * 0.065f);
        gap = (int) (sw * 0.028f);
        tileW = (sw - side * 2 - gap * (COLS - 1)) / COLS;
        tileH = tileW * 3 / 5;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // размытый фон (две картинки — для плавной смены)
        bgA = ambientView();
        bgB = ambientView();
        bgB.setAlpha(0f);
        root.addView(bgA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bgB, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33000000, 0x80000000, 0xE6000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        // контент
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setSmoothScrollingEnabled(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(0, (int) (sh * 0.40f), 0, (int) (sh * 0.12f));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setPadding(side, (int) (tileH * 0.12f), side, 0);
        dock = new LinearLayout(this);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        hs.addView(dock);
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        grid = new GridLayout(this);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(side, (int) (tileH * 0.2f), side, 0);
        col.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // часы справа сверху + точка VPN
        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        vpnDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(GREEN_1);
        vpnDot.setBackground(dot);
        vpnDot.setVisibility(View.GONE);
        int ds = (int) (sh * 0.012f);
        status.addView(vpnDot, Ui.lp(ds, ds, (int) (sh * 0.012f)));
        clock = Ui.text(this, sh * 0.03f, 0xCCF5F5F7, true);
        clock.setShadowLayer(10, 0, 2, 0x80000000);
        status.addView(clock);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sl.topMargin = (int) (sh * 0.05f);
        sl.rightMargin = side;
        root.addView(status, sl);
        return root;
    }

    private ImageView ambientView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        return v;
    }

    // ------------------------------------------------------------------
    private void reload() {
        io.execute(() -> {
            final Map<String, AppInfo> m = AppRepo.query(MainActivity.this);
            ui.post(() -> { AppRepo.cache = m; rebuild(); });
        });
    }

    private void rebuild() {
        Map<String, AppInfo> apps = AppRepo.cache;
        if (apps == null) return;
        Object keep = focusedTag;
        dock.removeAllViews();
        grid.removeAllViews();

        AppInfo vpn = AppRepo.findVpn(apps, prefs.vpnPackage());
        String vpnPkg = vpn == null ? null : vpn.pkg;

        // верхний ряд: VPN + главные приложения
        boolean on = Ui.isVpnActive(this);
        lastVpn = on;
        LinearLayout v = Ui.tile(this, TAG_VPN, null, glyph(R.drawable.ic_shield),
                on ? GREEN_1 : BLUE_1, on ? GREEN_2 : BLUE_2, 0.42f, "VPN", tileW, tileH, this::onTileFocus);
        vpnCard = Ui.card(v);
        vpnCard.setOnClickListener(x -> openVpn());
        vpnCard.setOnLongClickListener(x -> { showMenuFor(TAG_VPN); return true; });
        dock.addView(v, Ui.lp(-2, -2, gap));

        List<AppInfo> top = AppRepo.homeApps(apps, prefs, vpnPkg);
        for (AppInfo a : top) dock.addView(appTile(a), Ui.lp(-2, -2, gap));

        // сетка остальных + Настройки в конце
        for (AppInfo a : AppRepo.otherApps(apps, prefs, top, vpnPkg, showHidden)) {
            LinearLayout t = appTile(a);
            if (AppRepo.isHidden(a, prefs)) Ui.card(t).setAlpha(0.35f);
            addToGrid(t);
        }
        LinearLayout st = Ui.tile(this, TAG_SETTINGS, null, glyph(R.drawable.ic_gear),
                GRAY_1, GRAY_2, 0.5f, "Настройки", tileW, tileH, this::onTileFocus);
        Ui.card(st).setOnClickListener(x -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e) { Ui.toast(this, "Настройки недоступны"); }
        });
        Ui.card(st).setOnLongClickListener(x -> { showMenuFor(TAG_SETTINGS); return true; });
        addToGrid(st);

        // вернуть фокус
        View target = findCard(keep);
        (target != null ? target : vpnCard).requestFocus();
    }

    private LinearLayout appTile(final AppInfo a) {
        LinearLayout t = Ui.tile(this, a, a.banner, a.icon, Ui.shade(a.color, 1.12f), Ui.shade(a.color, 0.78f),
                0.6f, a.label, tileW, tileH, this::onTileFocus);
        Ui.card(t).setOnClickListener(x -> Ui.launch(this, a));
        Ui.card(t).setOnLongClickListener(x -> { showMenuFor(a); return true; });
        return t;
    }

    private void addToGrid(LinearLayout t) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        int col = grid.getChildCount() % COLS;
        lp.setMargins(0, 0, col == COLS - 1 ? 0 : gap, (int) (tileH * 0.32f));
        grid.addView(t, lp);
    }

    private View findCard(Object tag) {
        for (LinearLayout g : new LinearLayout[]{dock}) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = Ui.card((LinearLayout) g.getChildAt(i));
                if (same(c.getTag(), tag)) return c;
            }
        }
        for (int i = 0; i < grid.getChildCount(); i++) {
            View c = Ui.card((LinearLayout) grid.getChildAt(i));
            if (same(c.getTag(), tag)) return c;
        }
        return null;
    }

    private static boolean same(Object x, Object y) {
        if (x == y) return true;
        if (x instanceof AppInfo && y instanceof AppInfo) return ((AppInfo) x).pkg.equals(((AppInfo) y).pkg);
        return x != null && x.equals(y);
    }

    private Drawable glyph(int res) {
        return getResources().getDrawable(res, getTheme()).mutate();
    }

    // ------------------------------------------------------------------
    // Фокус → размытый фон
    // ------------------------------------------------------------------
    private void onTileFocus(View v, boolean has) {
        if (!has) return;
        focusedTag = v.getTag();
        if (isInDock(v)) scroll.smoothScrollTo(0, 0);
        setAmbient(focusedTag, false);
    }

    private boolean isInDock(View v) {
        ViewParent p = v.getParent();
        while (p != null) {
            if (p == dock) return true;
            p = p.getParent();
        }
        return false;
    }

    private void setAmbient(Object tag, boolean force) {
        if (!force && same(tag, bgTag)) return;
        bgTag = tag;
        Bitmap bmp;
        if (tag instanceof AppInfo && ((AppInfo) tag).ambient != null) bmp = ((AppInfo) tag).ambient;
        else if (TAG_VPN.equals(tag)) bmp = lastVpn ? AppRepo.ambientColors(GREEN_1, GREEN_2)
                : AppRepo.ambientColors(BLUE_1, BLUE_2);
        else bmp = AppRepo.ambientColors(GRAY_1, GRAY_2);

        BitmapDrawable d = new BitmapDrawable(getResources(), bmp);
        d.setFilterBitmap(true);
        final ImageView in = bgFront ? bgB : bgA;
        final ImageView out = bgFront ? bgA : bgB;
        bgFront = !bgFront;
        in.setImageDrawable(d);
        in.animate().alpha(1f).setDuration(600).start();
        out.animate().alpha(0f).setDuration(600).start();
    }

    // ------------------------------------------------------------------
    // VPN
    // ------------------------------------------------------------------
    private void openVpn() {
        AppInfo vpn = AppRepo.cache == null ? null : AppRepo.findVpn(AppRepo.cache, prefs.vpnPackage());
        if (vpn != null) { Ui.launch(this, vpn); return; }
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle("Happ не установлен")
                .setMessage("Установите Happ для Android TV или выберите другое приложение для этой кнопки.")
                .setPositiveButton("Выбрать", (d, w) -> chooseVpnApp())
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void chooseVpnApp() {
        if (AppRepo.cache == null) return;
        List<String> names = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        for (final AppInfo a : AppRepo.cache.values()) {
            names.add(a.label);
            acts.add(() -> { prefs.setVpnPackage(a.pkg); rebuild(); });
        }
        Ui.menu(this, "Приложение для VPN", names, acts);
    }

    private void checkVpn() {
        boolean on = Ui.isVpnActive(this);
        vpnDot.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on != lastVpn && vpnCard != null) {
            lastVpn = on;
            vpnCard.setBackground(Ui.vertical(on ? GREEN_1 : BLUE_1, on ? GREEN_2 : BLUE_2, tileH * 0.09f));
            if (TAG_VPN.equals(focusedTag)) setAmbient(TAG_VPN, true);
        }
    }

    // ------------------------------------------------------------------
    // Меню (долгое ОК или кнопка «Меню»)
    // ------------------------------------------------------------------
    private void showMenuFor(Object tag) {
        List<String> items = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (TAG_VPN.equals(tag)) {
            items.add("Выбрать приложение для VPN");
            acts.add(this::chooseVpnApp);
            Ui.menu(this, "VPN", items, acts);
            return;
        }
        if (TAG_SETTINGS.equals(tag)) {
            items.add(showHidden ? "Не показывать скрытые" : "Показать скрытые приложения");
            acts.add(() -> { showHidden = !showHidden; rebuild(); });
            Ui.menu(this, "Настройки", items, acts);
            return;
        }
        if (!(tag instanceof AppInfo)) return;
        final AppInfo a = (AppInfo) tag;
        final boolean onTop = AppRepo.onHome(a, prefs);
        final boolean hidden = prefs.hidden().contains(a.pkg);

        items.add("Открыть");
        acts.add(() -> Ui.launch(this, a));
        if (prefs.pinned().contains(a.pkg)) {
            items.add("Сдвинуть влево");
            acts.add(() -> move(a.pkg, -1));
            items.add("Сдвинуть вправо");
            acts.add(() -> move(a.pkg, 1));
        }
        items.add(onTop ? "Убрать из верхнего ряда" : "В верхний ряд");
        acts.add(() -> { AppRepo.setOnHome(a, prefs, !onTop); rebuild(); });
        items.add(hidden ? "Показать" : "Скрыть");
        acts.add(() -> {
            prefs.setFlag("hidden", a.pkg, !hidden);
            if (!hidden) { AppRepo.setOnHome(a, prefs, false); focusedTag = TAG_VPN; }
            rebuild();
        });
        items.add("О приложении");
        acts.add(() -> Ui.openAppDetails(this, a.pkg));
        Ui.menu(this, a.label, items, acts);
    }

    private void move(String pkg, int d) {
        List<String> p = prefs.pinned();
        int i = p.indexOf(pkg), j = i + d;
        if (i < 0 || j < 0 || j >= p.size()) return;
        p.remove(i);
        p.add(j, pkg);
        prefs.setPinned(p);
        rebuild();
    }

    // ------------------------------------------------------------------
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            clock.setText(new SimpleDateFormat("H:mm", new Locale("ru")).format(new Date()));
            ui.postDelayed(this, 1000 * (60 - (System.currentTimeMillis() / 1000) % 60));
        }
    };

    private final Runnable statusLoop = new Runnable() {
        @Override public void run() {
            checkVpn();
            ui.postDelayed(this, 3000);
        }
    };
}
