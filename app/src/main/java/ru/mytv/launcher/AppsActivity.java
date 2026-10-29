package ru.mytv.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Экран «Приложения»: сетка всех приложений. */
public class AppsActivity extends Activity {

    private static final int COLS = 6;
    private Prefs prefs;
    private GridLayout grid;
    private boolean showHidden = false;
    private String focusPkg;
    private int tileW, tileH, gap;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        int sw = getResources().getDisplayMetrics().widthPixels;
        int side = Ui.dp(this, 64);
        gap = Ui.dp(this, 34);
        tileW = (sw - side * 2 - gap * (COLS - 1)) / COLS;
        tileH = tileW * 3 / 5;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.screenBg());
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(side, Ui.dp(this, 44), side, Ui.dp(this, 60));
        col.setClipChildren(false);
        col.setClipToPadding(false);

        TextView title = Ui.text(this, 40, Ui.TEXT, 700);
        title.setText("Приложения");
        col.addView(title);

        grid = new GridLayout(this);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.topMargin = Ui.dp(this, 40);
        col.addView(grid, gl);

        scroll.addView(col);
        setContentView(scroll);
        build();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof AppInfo) { menu((AppInfo) f.getTag()); return true; }
        }
        return super.onKeyDown(keyCode, e);
    }

    private void build() {
        Map<String, AppInfo> apps = AppRepo.cache;
        if (apps == null) { apps = AppRepo.query(this); AppRepo.cache = apps; }
        grid.removeAllViews();
        Set<String> hidden = prefs.hidden();
        int hiddenCount = 0;
        View focusTarget = null;

        for (final AppInfo a : apps.values()) {
            boolean isHidden = hidden.contains(a.pkg) || (AppRepo.isJunk(a) && !prefs.pinned().contains(a.pkg));
            if (isHidden) hiddenCount++;
            if (isHidden && !showHidden) continue;
            LinearLayout t = Ui.tile(this, a, a.banner, a.icon, a.color, false, a.label, tileW, tileH, null);
            View card = Ui.card(t);
            if (isHidden) card.setAlpha(0.4f);
            card.setOnClickListener(v -> Ui.launch(this, a));
            card.setOnLongClickListener(v -> { menu(a); return true; });
            add(t);
            if (a.pkg.equals(focusPkg)) focusTarget = card;
        }

        if (hiddenCount > 0) {
            LinearLayout t = Ui.tile(this, "hidden", null, getResources().getDrawable(R.drawable.ic_eye, getTheme()),
                    Ui.GRAPHITE, true, showHidden ? "Спрятать скрытые" : "Скрытые · " + hiddenCount,
                    tileW, tileH, null);
            Ui.card(t).setOnClickListener(v -> { showHidden = !showHidden; focusPkg = null; build(); });
            add(t);
        }
        if (focusTarget != null) focusTarget.requestFocus();
        else if (grid.getChildCount() > 0) Ui.card((LinearLayout) grid.getChildAt(0)).requestFocus();
    }

    private void add(LinearLayout t) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.setMargins(0, 0, gap, Ui.dp(this, 20));
        grid.addView(t, lp);
    }

    private void menu(final AppInfo a) {
        focusPkg = a.pkg;
        List<String> items = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        final boolean home = AppRepo.onHome(a, prefs);
        final boolean hid = prefs.hidden().contains(a.pkg);
        items.add("Открыть");
        acts.add(() -> Ui.launch(this, a));
        items.add(home ? "Убрать с главного экрана" : "Добавить на главный экран");
        acts.add(() -> { AppRepo.setOnHome(a, prefs, !home); Ui.toast(this, home ? "Убрано" : "Добавлено"); });
        items.add(hid ? "Показать в списке" : "Скрыть из списка");
        acts.add(() -> { prefs.setFlag("hidden", a.pkg, !hid); if (!hid) AppRepo.setOnHome(a, prefs, false); build(); });
        items.add("О приложении · удалить");
        acts.add(() -> Ui.openAppDetails(this, a.pkg));
        Ui.menu(this, a.label, items, acts);
    }
}
