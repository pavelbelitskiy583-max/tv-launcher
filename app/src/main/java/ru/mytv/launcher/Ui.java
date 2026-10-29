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
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Плитки и мелочи в духе tvOS. */
final class Ui {
    static final int TEXT = 0xFFF5F5F7;
    static final float FOCUS_SCALE = 1.1f;

    interface OnFocus { void onFocus(View v, boolean has); }

    private Ui() { }

    static int dp(Activity a, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                a.getResources().getDisplayMetrics()));
    }

    static TextView text(Activity a, float px, int color, boolean medium) {
        TextView t = new TextView(a);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    static GradientDrawable vertical(int top, int bottom, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(radius);
        return g;
    }

    static int shade(int c, float f) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * f));
        return Color.HSVToColor(hsv);
    }

    /**
     * Плитка-иконка. Возвращает контейнер (иконка + подпись под ней);
     * фокусируемая карточка — первый ребёнок, у неё tag.
     */
    static LinearLayout tile(final Activity a, Object tag, Drawable banner, Drawable icon,
                             int top, int bottom, float iconScale, String label,
                             final int w, final int h, final OnFocus extra) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        final FrameLayout card = new FrameLayout(a);
        card.setTag(tag);
        card.setFocusable(true);
        card.setClickable(true);
        float r = h * 0.09f;
        card.setBackground(vertical(top, bottom, r));
        card.setClipToOutline(true);

        ImageView img = new ImageView(a);
        if (banner != null) {
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setImageDrawable(icon);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = (int) (h * iconScale);
            card.addView(img, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }

        // блик, который пробегает по иконке при наведении
        final View sheen = new View(a);
        GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF});
        sheen.setBackground(sg);
        sheen.setRotation(18);
        sheen.setTranslationX(-w);
        card.addView(sheen, new FrameLayout.LayoutParams((int) (w * 0.5f), (int) (h * 1.6f), Gravity.CENTER_VERTICAL));

        // тонкий верхний свет — как у стеклянных иконок Apple
        View rim = new View(a);
        rim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x26FFFFFF, 0x00FFFFFF}));
        card.addView(rim, new FrameLayout.LayoutParams(-1, (int) (h * 0.45f), Gravity.TOP));

        box.addView(card, new LinearLayout.LayoutParams(w, h));

        final TextView caption = text(a, h * 0.13f, TEXT, true);
        caption.setText(label);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        caption.setAlpha(0f);
        caption.setShadowLayer(8, 0, 2, 0x99000000);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(w, -2);
        cl.topMargin = (int) (h * 0.16f);
        box.addView(caption, cl);

        final PathInterpolator ease = new PathInterpolator(0.2f, 0.9f, 0.25f, 1f);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                v.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                        .translationZ(has ? h * 0.18f : 0f).setDuration(260).setInterpolator(ease).start();
                caption.animate().alpha(has ? 1f : 0f).translationY(has ? h * 0.05f : 0f)
                        .setDuration(240).start();
                if (has) {
                    sheen.setTranslationX(-w * 0.7f);
                    sheen.animate().translationX(w * 1.2f).setStartDelay(120).setDuration(750)
                            .setInterpolator(new DecelerateInterpolator(1.5f)).start();
                }
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

    static LinearLayout.LayoutParams lp(int w, int h, int right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.rightMargin = right;
        return p;
    }
}
