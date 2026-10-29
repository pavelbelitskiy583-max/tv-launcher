package ru.mytv.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Одно установленное приложение. */
final class AppInfo {
    final String label;
    final String pkg;
    final ComponentName component;
    final boolean isTvApp;      // приложение заявлено для ТВ (leanback)
    Drawable banner;            // широкая ТВ-картинка 16:9, если есть
    Drawable icon;

    AppInfo(String label, String pkg, ComponentName component, boolean isTvApp) {
        this.label = label;
        this.pkg = pkg;
        this.component = component;
        this.isTvApp = isTvApp;
    }

    Intent launchIntent() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(isTvApp ? "android.intent.category.LEANBACK_LAUNCHER" : Intent.CATEGORY_LAUNCHER);
        i.setComponent(component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }
}
