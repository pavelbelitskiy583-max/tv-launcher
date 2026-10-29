package ru.mytv.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Главный экран в стиле Apple TV. */
public class MainActivity extends Activity {

    private static final int APPLE_GREEN = 0xFF30D158;
    private static final String TAG_VPN = "vpn", TAG_ALL = "all", TAG_SETTINGS = "settings";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Prefs prefs;

    private View glow;
    private int glowColor = Ui.GRAPHITE;
    private ValueAnimator glowAnim;
    private TextView clock, vpnPill, heroTitle, heroSub;
    private LinearLayout row;
    private HorizontalScrollView rowScroll;
    private View vpnCard;
    private Object focusedTag = TAG_VPN;
    private int tileW, tileH;

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
        if (AppRepo.cache == null) reload(); else rebuildRow();
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
        if (row.getChildCount() > 0) {
            rowScroll.smoothScrollTo(0, 0);
            Ui.card((LinearLayout) row.getChildAt(0)).requestFocus();
        }
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
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.screenBg());
        root.setClipChildren(false);

        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        int side = Ui.dp(this, 64), gap = Ui.dp(this, 34);
        tileW = (sw - side * 2 - gap * 5) / 6;
        tileH = tileW * 3 / 5;

        // цветное свечение выбранного приложения
        glow = new View(this);
        root.addView(glow, new FrameLayout.LayoutParams(-1, (int) (sh * 0.8f)));
        setGlow(Ui.GRAPHITE, false);

        // часы и индикатор VPN — справа сверху
        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        vpnPill = Ui.text(this, 15, 0xFF000000, 700);
        vpnPill.setText("VPN");
        vpnPill.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        vpnPill.setBackground(Ui.rounded(APPLE_GREEN, Ui.dp(this, 8)));
        vpnPill.setVisibility(View.GONE);
        status.addView(vpnPill, Ui.lp(-2, -2, Ui.dp(this, 16)));
        clock = Ui.text(this, 24, Ui.TEXT, 500);
        status.addView(clock);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sl.topMargin = Ui.dp(this, 40);
        sl.rightMargin = side;
        root.addView(status, sl);

        // крупное название выбранного
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        heroTitle = Ui.text(this, 54, Ui.TEXT, 700);
        heroSub = Ui.text(this, 21, Ui.TEXT_2, 400);
        hero.addView(heroTitle);
        LinearLayout.LayoutParams hs = new LinearLayout.LayoutParams(-2, -2);
        hs.topMargin = Ui.dp(this, 12);
        hero.addView(heroSub, hs);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        hl.leftMargin = side;
        hl.bottomMargin = Ui.dp(this, 64) + tileH + Ui.dp(this, 90);
        root.addView(hero, hl);

        // ряд плиток
        rowScroll = new HorizontalScrollView(this);
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.setClipChildren(false);
        rowScroll.setClipToPadding(false);
        rowScroll.setPadding(side, Ui.dp(this, 30), side, 0);
        row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        rowScroll.addView(row);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        rl.bottomMargin = Ui.dp(this, 40);
        root.addView(rowScroll, rl);
        return root;
    }

    // ------------------------------------------------------------------
    private void reload() {
        io.execute(() -> {
            final Map<String, AppInfo> m = AppRepo.query(MainActivity.this);
            ui.post(() -> { AppRepo.cache = m; rebuildRow(); });
        });
    }

    private void rebuildRow() {
        Map<String, AppInfo> apps = AppRepo.cache;
        if (apps == null) return;
        Object keep = focusedTag;
        row.removeAllViews();
        int gap = Ui.dp(this, 34);
        AppInfo vpn = AppRepo.findVpn(apps, prefs.vpnPackage());

        // VPN
        LinearLayout v = Ui.tile(this, TAG_VPN, null, glyph(R.drawable.ic_shield), Ui.GRAPHITE, true,
                "VPN", tileW, tileH, this::onTileFocus);
        vpnCard = Ui.card(v);
        vpnCard.setOnClickListener(x -> openVpn());
        vpnCard.setOnLongClickListener(x -> { showMenuFor(TAG_VPN); return true; });
        row.addView(v, Ui.lp(-2, -2, gap));
        paintVpn();

        // приложения
        for (final AppInfo a : AppRepo.homeApps(apps, prefs, vpn == null ? null : vpn.pkg)) {
            LinearLayout t = Ui.tile(this, a, a.banner, a.icon, a.color, false, a.label, tileW, tileH,
                    this::onTileFocus);
            Ui.card(t).setOnClickListener(x -> Ui.launch(this, a));
            Ui.card(t).setOnLongClickListener(x -> { showMenuFor(a); return true; });
            row.addView(t, Ui.lp(-2, -2, gap));
        }

        // все приложения и настройки
        LinearLayout all = Ui.tile(this, TAG_ALL, null, glyph(R.drawable.ic_grid), Ui.GRAPHITE, true,
                "Приложения", tileW, tileH, this::onTileFocus);
        Ui.card(all).setOnClickListener(x -> startActivity(new Intent(this, AppsActivity.class)));
        row.addView(all, Ui.lp(-2, -2, gap));

        LinearLayout st = Ui.tile(this, TAG_SETTINGS, null, glyph(R.drawable.ic_gear), Ui.GRAPHITE, true,
                "Настройки", tileW, tileH, this::onTileFocus);
        Ui.card(st).setOnClickListener(x -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e) { Ui.toast(this, "Настройки недоступны"); }
        });
        row.addView(st, Ui.lp(-2, -2, 0));

        // вернуть фокус туда, где был
        View target = null;
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = Ui.card((LinearLayout) row.getChildAt(i));
            Object t = c.getTag();
            if (t == keep || (t instanceof AppInfo && keep instanceof AppInfo
                    && ((AppInfo) t).pkg.equals(((AppInfo) keep).pkg))) target = c;
        }
        (target != null ? target : vpnCard).requestFocus();
    }

    private Drawable glyph(int res) {
        Drawable d = getResources().getDrawable(res, getTheme());
        return d.mutate();
    }

    // ------------------------------------------------------------------
    private void onTileFocus(View v, boolean has) {
        if (!has) return;
        focusedTag = v.getTag();
        Object t = v.getTag();
        if (t instanceof AppInfo) {
            AppInfo a = (AppInfo) t;
            heroTitle.setText(a.label);
            heroSub.setText("");
            setGlow(a.color, true);
        } else if (TAG_VPN.equals(t)) {
            updateVpnHero();
        } else if (TAG_ALL.equals(t)) {
            heroTitle.setText("Приложения");
            heroSub.setText("Все установленные приложения");
            setGlow(0xFF0A84FF, true);
        } else if (TAG_SETTINGS.equals(t)) {
            heroTitle.setText("Настройки");
            heroSub.setText("Сеть, изображение, звук, источники");
            setGlow(0xFF8E8E93, true);
        }
    }

    private void setGlow(int color, boolean animate) {
        if (glowAnim != null) glowAnim.cancel();
        if (!animate) { applyGlow(color); glowColor = color; return; }
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, color);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(an -> { glowColor = (Integer) an.getAnimatedValue(); applyGlow(glowColor); });
        glowAnim.start();
    }

    private void applyGlow(int c) {
        int base = Ui.darken(c, 0.55f);
        int top = (0x8C << 24) | (base & 0xFFFFFF);
        int mid = (0x30 << 24) | (base & 0xFFFFFF);
        glow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, mid, 0x00000000}));
    }

    // ------------------------------------------------------------------
    // VPN
    // ------------------------------------------------------------------
    private void openVpn() {
        AppInfo vpn = AppRepo.cache == null ? null : AppRepo.findVpn(AppRepo.cache, prefs.vpnPackage());
        if (vpn != null) { Ui.launch(this, vpn); return; }
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle("Happ не установлен")
                .setMessage("Установите Happ для Android TV. Если он уже стоит под другим названием — выберите его.")
                .setPositiveButton("Выбрать", (d, w) -> chooseVpnApp())
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void chooseVpnApp() {
        if (AppRepo.cache == null) return;
        final List<AppInfo> list = new ArrayList<>(AppRepo.cache.values());
        List<String> names = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        for (final AppInfo a : list) {
            names.add(a.label);
            acts.add(() -> { prefs.setVpnPackage(a.pkg); rebuildRow(); });
        }
        Ui.menu(this, "Приложение для кнопки VPN", names, acts);
    }

    private void paintVpn() {
        if (vpnCard == null) return;
        boolean on = Ui.isVpnActive(this);
        vpnCard.setBackground(Ui.rounded(on ? APPLE_GREEN : Ui.GRAPHITE, Ui.dp(this, 14)));
        vpnPill.setVisibility(on ? View.VISIBLE : View.GONE);
        if (TAG_VPN.equals(focusedTag) && vpnCard.hasFocus()) updateVpnHero();
    }

    private void updateVpnHero() {
        boolean on = Ui.isVpnActive(this);
        AppInfo vpn = AppRepo.cache == null ? null : AppRepo.findVpn(AppRepo.cache, prefs.vpnPackage());
        heroTitle.setText("VPN");
        if (vpn == null) heroSub.setText("Happ не установлен");
        else heroSub.setText(on ? "Подключено · " + vpn.label : "Не подключено · откройте, чтобы включить");
        setGlow(on ? APPLE_GREEN : 0xFF636366, true);
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
        if (!(tag instanceof AppInfo)) return;
        final AppInfo a = (AppInfo) tag;
        items.add("Открыть");
        acts.add(() -> Ui.launch(this, a));
        final List<String> pin = prefs.pinned();
        if (pin.contains(a.pkg)) {
            items.add("Переместить влево");
            acts.add(() -> { move(a.pkg, -1); });
            items.add("Переместить вправо");
            acts.add(() -> { move(a.pkg, 1); });
        }
        items.add("Убрать с главного экрана");
        acts.add(() -> { AppRepo.setOnHome(a, prefs, false); focusedTag = TAG_VPN; rebuildRow(); });
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
        rebuildRow();
    }

    // ------------------------------------------------------------------
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            clock.setText(new SimpleDateFormat("HH:mm", new Locale("ru")).format(new Date()));
            ui.postDelayed(this, 1000 * (60 - (System.currentTimeMillis() / 1000) % 60));
        }
    };

    private final Runnable statusLoop = new Runnable() {
        @Override public void run() {
            paintVpn();
            ui.postDelayed(this, 3000);
        }
    };
}
