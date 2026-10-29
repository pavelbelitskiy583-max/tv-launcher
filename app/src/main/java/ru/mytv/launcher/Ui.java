package ru.mytv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Общие элементы интерфейса в стиле tvOS. */
final class Ui {
    static final int BG_TOP = 0xFF16161A;
    static final int BG_BOTTOM = 0xFF000000;
    static final int GRAPHITE = 0xFF2C2C2E;
    static final int TEXT = 0xFFF5F5F7;
    static final int TEXT_2 = 0x99EBEBF5;   // вторичный текст Apple
    static final float FOCUS_SCALE = 1.12f;

    interface OnFocus { void onFocus(View v, boolean has); }

    private Ui() { }

    static int dp(Activity a, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                a.getResources().getDisplayMetrics()));
    }

    static TextView text(Activity a, float sp, int color, int weight) {
        TextView t = new TextView(a);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        String fam = weight >= 700 ? "sans-serif" : weight >= 500 ? "sans-serif-medium" : "sans-serif-light";
        t.setTypeface(Typeface.create(fam, weight >= 700 ? Typeface.BOLD : Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= 21) t.setLetterSpacing(weight >= 700 ? -0.01f : 0f);
        return t;
    }

    static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /**
     * Плитка + подпись под ней (видна только в фокусе, как в tvOS).
     * Возвращает контейнер; фокусируемая плитка — первый ребёнок, у неё tag.
     */
    static LinearLayout tile(final Activity a, Object tag, Drawable banner, Drawable icon, int color,
                             boolean glyph, String label, int w, int h, final OnFocus extra) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        final FrameLayout card = new FrameLayout(a);
        card.setTag(tag);
        card.setFocusable(true);
        card.setClickable(true);
        float r = dp(a, 14);
        card.setBackground(rounded(banner != null ? Color.BLACK : (glyph ? GRAPHITE : color), r));
        if (Build.VERSION.SDK_INT >= 21) card.setClipToOutline(true);

        ImageView img = new ImageView(a);
        if (banner != null) {
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setImageDrawable(icon);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = (int) (h * (glyph ? 0.36f : 0.52f));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
            card.addView(img, lp);
            if (!glyph) {
                // мягкий блик сверху — «стекло» как у иконок Apple
                View sheen = new View(a);
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0x33FFFFFF, 0x00FFFFFF, 0x22000000});
                sheen.setBackground(g);
                card.addView(sheen, new FrameLayout.LayoutParams(-1, -1));
            }
        }
        box.addView(card, new LinearLayout.LayoutParams(w, h));

        final TextView caption = text(a, 17, TEXT, 500);
        caption.setText(label);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        caption.setAlpha(0f);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(w, -2);
        cl.topMargin = dp(a, 18);
        box.addView(caption, cl);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                v.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                        .translationZ(has ? dp(a, 18) : 0).setDuration(220)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
                caption.animate().alpha(has ? 1f : 0f).translationY(has ? dp(a, 6) : 0)
                        .setDuration(220).start();
                if (extra != null) extra.onFocus(v, has);
            }
        });
        return box;
    }

    static View card(LinearLayout box) { return box.getChildAt(0); }

    static boolean isVpnActive(Activity a) {
        try {
            ConnectivityManager cm = (ConnectivityManager) a.getSystemService(Activity.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    static void launch(Activity a, AppInfo app) {
        try {
            a.startActivity(app.launchIntent());
            return;
        } catch (Exception ignored) { }
        Intent i = a.getPackageManager().getLaunchIntentForPackage(app.pkg);
        if (i == null) i = a.getPackageManager().getLeanbackLaunchIntentForPackage(app.pkg);
        if (i != null) {
            try { a.startActivity(i); return; } catch (Exception ignored) { }
        }
        toast(a, "Не удалось открыть «" + app.label + "»");
    }

    static void openAppDetails(Activity a, String pkg) {
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { toast(a, "Экран недоступен на этом ТВ"); }
    }

    static void menu(Activity a, String title, final List<String> items, final List<Runnable> acts) {
        new AlertDialog.Builder(a, R.style.Dialog)
                .setTitle(title)
                .setItems(items.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    static void toast(Activity a, String s) { Toast.makeText(a, s, Toast.LENGTH_SHORT).show(); }

    /** Фон экрана: почти чёрный с лёгким градиентом. */
    static GradientDrawable screenBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG_BOTTOM});
    }

    static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    static LinearLayout.LayoutParams lp(int w, int h, int right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.rightMargin = right;
        return p;
    }
}
