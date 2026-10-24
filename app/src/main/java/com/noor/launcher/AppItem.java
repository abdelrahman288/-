package com.noor.launcher;

import android.content.ComponentName;
import android.graphics.Bitmap;

final class AppItem {
    final String pkg;
    final String label;
    final ComponentName cn;
    final Bitmap icon; // 96x96 جاهزة للرسم

    AppItem(String pkg, String label, ComponentName cn, Bitmap icon) {
        this.pkg = pkg;
        this.label = label;
        this.cn = cn;
        this.icon = icon;
    }
}
