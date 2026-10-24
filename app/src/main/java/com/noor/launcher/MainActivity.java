package com.noor.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends Activity implements HomeView.Host {

    private HomeView home;
    private MediaPlayer radio;
    private boolean radioLoading;
    private boolean receiverOn;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppRepo.init(this);
        try {
            AppRepo.UI = Typeface.createFromAsset(getAssets(), "fonts/ui.ttf"); // اختياري: ضع خط عربي هنا
        } catch (Exception ignored) {
            AppRepo.UI = Typeface.DEFAULT;
        }
        home = new HomeView(this, this);
        setContentView(home);
        home.requestFocus();
        reloadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addAction(Intent.ACTION_PACKAGE_REPLACED);
            f.addDataScheme("package");
            registerReceiver(pkgReceiver, f);
            receiverOn = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (receiverOn) {
            unregisterReceiver(pkgReceiver);
            receiverOn = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        home.start();
    }

    @Override
    protected void onPause() {
        home.stop();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // ضغطة زر Home وإحنا مفتوحين: رجّع للشاشة الرئيسية
        home.goHome();
    }

    @Override
    public void onBackPressed() {
        // لانشر: زر الرجوع ما يقفلش الشاشة الرئيسية
        home.onBack();
    }

    @Override
    protected void onDestroy() {
        stopRadio();
        super.onDestroy();
    }

    private void reloadApps() {
        new Thread(() -> {
            AppRepo.load(getApplicationContext());
            runOnUiThread(() -> home.onAppsLoaded());
        }).start();
    }

    // ---------------- Host ----------------

    @Override
    public void launch(AppItem a) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(a.cn);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception e) {
            toast("تعذّر فتح التطبيق");
        }
    }

    @Override
    public void openSystem(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                toast("الإعدادات غير متاحة");
            }
        }
    }

    @Override
    public void appInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("تعذّر فتح معلومات التطبيق");
        }
    }

    @Override
    public boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------- الإذاعة ----------------

    @Override
    public int radioState() {
        if (radio == null) return 0;
        return radioLoading ? 1 : 2;
    }

    @Override
    public void toggleRadio() {
        if (radio != null) {
            stopRadio();
            home.invalidate();
            return;
        }
        if (!online()) {
            toast("لا يوجد اتصال بالإنترنت");
            return;
        }
        try {
            radioLoading = true;
            radio = new MediaPlayer();
            radio.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build());
            radio.setDataSource(Config.RADIO_URL);
            radio.setOnPreparedListener(mp -> {
                radioLoading = false;
                mp.start();
                home.invalidate();
            });
            radio.setOnErrorListener((mp, what, extra) -> {
                stopRadio();
                toast("تعذّر تشغيل الإذاعة");
                home.invalidate();
                return true;
            });
            radio.prepareAsync();
        } catch (Exception e) {
            stopRadio();
            toast("تعذّر تشغيل الإذاعة");
        }
        home.invalidate();
    }

    private void stopRadio() {
        radioLoading = false;
        if (radio != null) {
            try {
                radio.release();
            } catch (Exception ignored) {
            }
            radio = null;
        }
    }
}
