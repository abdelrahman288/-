package com.noor.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * الواجهة كلها مرسومة على Canvas واحد (بدون Layouts ولا RecyclerView) = أخف وأسلس ما يمكن.
 * كل الإحداثيات في مساحة تصميم ثابتة 1920x1080 ثم تتحجّم تلقائياً لحجم الشاشة.
 */
final class HomeView extends View {

    interface Host {
        void launch(AppItem a);
        void openSystem(String action);
        void appInfo(String pkg);
        boolean online();
        void toast(String s);
        int radioState(); // 0 مقفول، 1 جارٍ الاتصال، 2 شغال
        void toggleRadio();
    }

    // ---------------- ثوابت ----------------
    static final int HOME = 0, APPS = 1, PRAYERS = 2;
    static final float W = 1920f, H = 1080f;

    static final int C_BG1 = 0xFF14275C, C_BG2 = 0xFF08122E;
    static final int C_TILE = 0xFF1A2C62, C_TILE_F = 0xFF2B4592;
    static final int C_TXT = 0xFFFFFFFF, C_DIM = 0xFF9FB0D9, C_GOLD = 0xFFF2C14E;
    static final int C_NOW = 0xFFF3F6FF, C_DARK = 0xFF0B1533, C_PANEL = 0xFF0D1A44;
    static final int C_BAD = 0xFFFF8A80;

    static final int K_SETTINGS = 0, K_NET = 1, K_RADIO = 2, K_PRAYER = 3, K_FAV = 4, K_MORE = 5,
            K_ALL = 6, K_BTN = 7, K_APP = 8, K_CITY = 9, K_HADJ = 10, K_BACK = 11;

    // المحراب
    static final float PX = 1240f, PY = 64f, PW = 600f, PH = 952f;
    static final float LEFT_R = 1170f; // الحافة اليمنى للمنطقة اليسرى (RTL)

    // شبكة التطبيقات
    static final int COLS = 8;
    static final float GRID_TOP = 190f, CELL_W = 190f, CELL_H = 180f, GAP_X = 24f, ROW_STEP = 204f, GRID_R = 1804f;
    static final float VIEW_TOP = 170f, VIEW_BOT = 1040f;

    static final class El {
        final int kind, idx;
        final RectF r = new RectF();
        AppItem app;
        String label = "";
        float f; // 0..1 حركة التركيز

        El(int kind, int idx) {
            this.kind = kind;
            this.idx = idx;
        }

        float cx() { return r.centerX(); }
        float cy() { return r.centerY(); }
    }

    private final Host host;

    // الرسم
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint pBg, pGrad;
    private final Path arch = new Path();
    private final DashPathEffect dash = new DashPathEffect(new float[]{14, 10}, 0);
    private final RectF tmpR = new RectF();
    private final Rect srcR = new Rect(0, 0, AppRepo.ICON, AppRepo.ICON);
    private final Typeface ui, uiBold, serif, serifBold;

    // الحالة
    private int mode = HOME;
    private final ArrayList<El> homeEls = new ArrayList<>();
    private final ArrayList<El> appEls = new ArrayList<>();
    private final ArrayList<El> prEls = new ArrayList<>();
    private int hf, af, pf;
    private float scroll, scrollTarget;
    private long lastFrame;
    private float sc = 1f, ox, oy;

    // المواقيت
    private int cityIdx, hadj;
    private City city;
    private TimeZone tz;
    private Calendar cal;
    private final long[] today = new long[6];
    private long tomorrowFajr;
    private int dayKey = -1;
    private boolean dirty = true, ramadan, nextTomorrow, netOk;
    private int nowIdx = -1, nextIdx = 0, netTick;
    private final String[] tFull = {"", "", "", "", "", ""};
    private final String[] tLabel = new String[6];
    private String clockDigits = "", clockSfx = "", dateStr = "", hijriStr = "", dateLine = "";
    private String cdStr = "", panelLabel = "", panelName = "", panelAdhan = "", suhoorStr = "";
    private float clockW;
    private double qibla;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            postDelayed(this, 1000 - (System.currentTimeMillis() % 1000) + 5);
        }
    };

    HomeView(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDefaultFocusHighlightEnabled(false);

        ui = AppRepo.UI;
        uiBold = Typeface.create(ui, Typeface.BOLD);
        serif = Typeface.SERIF;
        serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD);

        pStroke.setStyle(Paint.Style.STROKE);
        buildBackground();
        buildArch();

        cityIdx = AppRepo.prefs().getInt("city", 0);
        if (cityIdx < 0 || cityIdx >= City.ALL.length) cityIdx = 0;
        hadj = AppRepo.prefs().getInt("hadj", 0);
        applyCity();
        rebuildHome();
        rebuildPrayers();
        hf = defaultHomeFocus();
        tick();
    }

    // ---------------- دورة الحياة ----------------

    void start() {
        removeCallbacks(ticker);
        post(ticker);
    }

    void stop() {
        removeCallbacks(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    void onAppsLoaded() {
        rebuildApps();
        rebuildHome();
        kick();
    }

    void goHome() {
        mode = HOME;
        hf = defaultHomeFocus();
        kick();
    }

    boolean onBack() {
        if (mode != HOME) {
            mode = HOME;
            kick();
        }
        return true;
    }

    private void kick() {
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        sc = Math.min(w / W, h / H);
        ox = (w - W * sc) / 2f;
        oy = (h - H * sc) / 2f;
    }

    // ---------------- تجهيز ----------------

    private void buildBackground() {
        Bitmap t = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(t);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        p.setColor(0x16FFFFFF);
        Path d = new Path();
        d.moveTo(60, 6);
        d.lineTo(114, 60);
        d.lineTo(60, 114);
        d.lineTo(6, 60);
        d.close();
        c.drawPath(d, p);
        c.drawCircle(60, 60, 22, p);
        c.save();
        c.rotate(45, 60, 60);
        c.drawRect(44, 44, 76, 76, p);
        c.restore();
        pBg = new Paint();
        pBg.setShader(new BitmapShader(t, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        pGrad = new Paint();
        pGrad.setShader(new LinearGradient(0, 0, W, H, C_BG1, C_BG2, Shader.TileMode.CLAMP));
    }

    private void buildArch() {
        arch.moveTo(PX, PY + PH);
        arch.lineTo(PX, PY + 330);
        arch.cubicTo(PX, PY + 150, PX + PW * 0.30f, PY + 40, PX + PW / 2f, PY);
        arch.cubicTo(PX + PW * 0.70f, PY + 40, PX + PW, PY + 150, PX + PW, PY + 330);
        arch.lineTo(PX + PW, PY + PH);
        arch.close();
    }

    private void applyCity() {
        city = City.ALL[cityIdx];
        tz = TimeZone.getTimeZone(city.zone);
        cal = Calendar.getInstance(tz);
        qibla = Prayer.qibla(city);
        dirty = true;
    }

    private El add(ArrayList<El> l, int kind, int idx, float left, float top, float right, float bottom) {
        El e = new El(kind, idx);
        e.r.set(left, top, right, bottom);
        l.add(e);
        return e;
    }

    private String ellipsize(String s, float size, float maxW, Typeface tf) {
        mp.setTypeface(tf);
        mp.setTextSize(size);
        return TextUtils.ellipsize(s, mp, maxW, TextUtils.TruncateAt.END).toString();
    }

    private int defaultHomeFocus() {
        for (int i = 0; i < homeEls.size(); i++) if (homeEls.get(i).kind == K_FAV) return i;
        for (int i = 0; i < homeEls.size(); i++) if (homeEls.get(i).kind == K_PRAYER) return i;
        return 0;
    }

    private void rebuildHome() {
        int fk = -1, fi = 0;
        if (hf >= 0 && hf < homeEls.size()) {
            fk = homeEls.get(hf).kind;
            fi = homeEls.get(hf).idx;
        }
        homeEls.clear();
        add(homeEls, K_SETTINGS, 0, 64, 64, 128, 128);
        add(homeEls, K_NET, 0, 148, 64, 418, 128);
        add(homeEls, K_RADIO, 0, 434, 64, 664, 128);

        for (int i = 0; i < 6; i++) {
            float right = LEFT_R - i * 186f;
            add(homeEls, K_PRAYER, i, right - 170, 350, right, 500);
        }

        List<AppItem> favs = AppRepo.resolve(AppRepo.KEY_FAVS);
        for (int i = 0; i < favs.size() && i < Config.MAX_FAVS; i++) {
            float right = LEFT_R - i * 280f;
            El e = add(homeEls, K_FAV, i, right - 264, 572, right, 722);
            e.app = favs.get(i);
            e.label = ellipsize(e.app.label, 30, 264 - 44, ui);
        }

        List<AppItem> more = AppRepo.resolve(AppRepo.KEY_MORE);
        int n = Math.min(more.size(), Config.MAX_MORE);
        for (int i = 0; i < n; i++) {
            float right = LEFT_R - i * 186f;
            El e = add(homeEls, K_MORE, i, right - 170, 794, right, 924);
            e.app = more.get(i);
            e.label = ellipsize(e.app.label, 26, 170 - 36, ui);
        }
        float allRight = LEFT_R - n * 186f;
        add(homeEls, K_ALL, n, allRight - 170, 794, allRight, 924);

        add(homeEls, K_BTN, 0, PX + 50, 900, PX + PW - 50, 974);

        hf = -1;
        for (int i = 0; i < homeEls.size(); i++) {
            if (homeEls.get(i).kind == fk && homeEls.get(i).idx == fi) hf = i;
        }
        if (hf < 0) hf = defaultHomeFocus();
    }

    private void rebuildApps() {
        appEls.clear();
        List<AppItem> l = AppRepo.apps;
        for (int i = 0; i < l.size(); i++) {
            int row = i / COLS, col = i % COLS;
            float right = GRID_R - col * (CELL_W + GAP_X);
            float top = GRID_TOP + row * ROW_STEP;
            El e = add(appEls, K_APP, i, right - CELL_W, top, right, top + CELL_H);
            e.app = l.get(i);
            e.label = ellipsize(e.app.label, 24, CELL_W - 20, ui);
        }
        if (af >= appEls.size()) af = Math.max(0, appEls.size() - 1);
    }

    private void rebuildPrayers() {
        prEls.clear();
        add(prEls, K_BACK, 0, 116, 64, 296, 128);
        add(prEls, K_CITY, 0, 1384, 170, 1804, 236);
        add(prEls, K_HADJ, 0, 944, 170, 1364, 236);
        pf = 1;
    }

    private void setMode(int m) {
        mode = m;
        if (m == APPS) {
            rebuildApps();
            af = 0;
            scroll = 0;
            scrollTarget = 0;
        } else if (m == PRAYERS) {
            pf = 1;
        }
        kick();
    }

    // ---------------- الوقت والمواقيت ----------------

    void tick() {
        long now = System.currentTimeMillis();
        cal.setTimeInMillis(now);
        int y = cal.get(Calendar.YEAR), m = cal.get(Calendar.MONTH) + 1, d = cal.get(Calendar.DAY_OF_MONTH);
        int key = y * 10000 + m * 100 + d;
        if (dirty || key != dayKey) {
            recomputeDay(y, m, d);
            dayKey = key;
            dirty = false;
        }

        nowIdx = -1;
        for (int i = 0; i < 6; i++) if (now >= today[i]) nowIdx = i;
        nextIdx = -1;
        int[] prayers = {Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA};
        for (int p : prayers) {
            if (today[p] > now) {
                nextIdx = p;
                break;
            }
        }
        long nextTime;
        if (nextIdx < 0) {
            nextIdx = Prayer.FAJR;
            nextTime = tomorrowFajr;
            nextTomorrow = true;
        } else {
            nextTime = today[nextIdx];
            nextTomorrow = false;
        }

        for (int i = 0; i < 6; i++) {
            String lab = null;
            if (i == nowIdx) lab = "الآن";
            else if (i == nextIdx) lab = "التالية";
            else if (ramadan && i == Prayer.FAJR) lab = "الإمساك";
            else if (ramadan && i == Prayer.MAGHRIB) lab = "الإفطار";
            tLabel[i] = lab;
        }

        boolean iftar = ramadan && nextIdx == Prayer.MAGHRIB;
        panelLabel = iftar ? "الإفطار بعد" : (ramadan && nextIdx == Prayer.FAJR ? "الإمساك بعد" : "الصلاة القادمة");
        panelName = iftar ? "الإفطار" : Prayer.NAMES[nextIdx];
        cdStr = Fmt.countdown(nextTime - now);
        panelAdhan = "الأذان " + Fmt.hm(nextTime, tz) + " " + Fmt.ampm(nextTime, tz) + "، " + city.name;
        suhoorStr = ramadan ? "السحور حتى " + Fmt.hm(today[Prayer.FAJR], tz) + " " + Fmt.ampm(today[Prayer.FAJR], tz) : "";

        clockDigits = Fmt.hm(now, tz);
        clockSfx = Fmt.ampm(now, tz);
        mp.setTypeface(uiBold);
        mp.setTextSize(124);
        clockW = mp.measureText(clockDigits);

        if (netTick++ % 5 == 0) netOk = host.online();
        invalidate();
    }

    private void recomputeDay(int y, int m, int d) {
        long[] t = Prayer.times(y, m, d, city);
        System.arraycopy(t, 0, today, 0, 6);
        LocalDate nx = LocalDate.of(y, m, d).plusDays(1);
        tomorrowFajr = Prayer.times(nx.getYear(), nx.getMonthValue(), nx.getDayOfMonth(), city)[Prayer.FAJR];
        for (int i = 0; i < 6; i++) tFull[i] = Fmt.hm(today[i], tz) + " " + Fmt.ampm(today[i], tz);
        int[] h = Fmt.hijri(y, m, d, hadj);
        ramadan = h[1] == 9;
        hijriStr = Fmt.hijriText(h, city.name);
        dateStr = Fmt.gregorian(cal);
        dateLine = dateStr + "   •   " + Fmt.digits(h[0]) + " " + Fmt.HIJRI[h[1] - 1] + " " + Fmt.digits(h[2]) + " هـ";
    }

    // ---------------- الرسم ----------------

    private static int lerp(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align al, Typeface tf) {
        if (s == null || s.isEmpty()) return;
        pText.setTypeface(tf);
        pText.setTextSize(size);
        pText.setColor(color);
        pText.setTextAlign(al);
        c.drawText(s, x, y, pText);
    }

    private ArrayList<El> cur() {
        return mode == HOME ? homeEls : (mode == APPS ? appEls : prEls);
    }

    private int curFocus() {
        return mode == HOME ? hf : (mode == APPS ? af : pf);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, Math.max(0f, (now - lastFrame) / 1000f));
        lastFrame = now;
        float k = Math.min(1f, dt * 16f);
        boolean anim = false;

        ArrayList<El> els = cur();
        int fi = curFocus();
        for (int i = 0; i < els.size(); i++) {
            El e = els.get(i);
            float t = (i == fi) ? 1f : 0f;
            if (e.f != t) {
                e.f += (t - e.f) * k;
                if (Math.abs(t - e.f) < 0.01f) e.f = t;
                else anim = true;
            }
        }
        if (mode == APPS && scroll != scrollTarget) {
            scroll += (scrollTarget - scroll) * k;
            if (Math.abs(scrollTarget - scroll) < 0.5f) scroll = scrollTarget;
            else anim = true;
        }

        c.drawColor(0xFF08122E);
        c.save();
        c.translate(ox, oy);
        c.scale(sc, sc);
        c.drawRect(0, 0, W, H, pGrad);
        c.drawRect(0, 0, W, H, pBg);

        if (mode == HOME) drawHome(c);
        else if (mode == APPS) drawApps(c);
        else drawPrayers(c);
        c.restore();

        if (anim) postInvalidateOnAnimation();
    }

    private void ring(Canvas c, El e, float rad) {
        if (e.f > 0.02f) {
            pStroke.setPathEffect(null);
            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setStrokeWidth(4);
            pStroke.setColor(((int) (255 * e.f) << 24) | 0x00FFFFFF);
            c.drawRoundRect(e.r, rad, rad, pStroke);
        }
    }

    private void fillTile(Canvas c, El e, float rad, int fill, int fillF) {
        pFill.setColor(lerp(fill, fillF, e.f));
        c.drawRoundRect(e.r, rad, rad, pFill);
    }

    private void begin(Canvas c, El e) {
        float s = 1f + 0.06f * e.f;
        c.save();
        c.scale(s, s, e.cx(), e.cy());
    }

    private void drawHome(Canvas c) {
        // الساعة والتاريخ
        text(c, clockDigits, LEFT_R, 205, 124, C_TXT, Paint.Align.RIGHT, uiBold);
        text(c, clockSfx, LEFT_R - clockW - 16, 205, 48, C_TXT, Paint.Align.RIGHT, uiBold);
        text(c, dateStr, LEFT_R, 268, 38, C_TXT, Paint.Align.RIGHT, ui);
        text(c, hijriStr, LEFT_R, 316, 30, C_DIM, Paint.Align.RIGHT, ui);

        text(c, "المفضلة", LEFT_R, 552, 28, C_DIM, Paint.Align.RIGHT, ui);
        text(c, "المزيد", LEFT_R, 774, 28, C_DIM, Paint.Align.RIGHT, ui);
        text(c, "اضغط OK للفتح   •   اضغط مطولاً على أي تطبيق للتعديل", LEFT_R, 1010, 24, C_DIM, Paint.Align.RIGHT, ui);

        boolean hasFav = false;
        for (El e : homeEls) if (e.kind == K_FAV) hasFav = true;
        if (!hasFav) {
            text(c, "افتح (كل التطبيقات) واضغط مطولاً على أي تطبيق لإضافته هنا", LEFT_R - 20, 660, 28, C_DIM, Paint.Align.RIGHT, ui);
        }

        drawPanel(c);

        for (int i = 0; i < homeEls.size(); i++) if (i != hf) drawHomeEl(c, homeEls.get(i));
        if (hf >= 0 && hf < homeEls.size()) drawHomeEl(c, homeEls.get(hf));
    }

    private void drawHomeEl(Canvas c, El e) {
        begin(c, e);
        switch (e.kind) {
            case K_SETTINGS: drawSettings(c, e); break;
            case K_NET: drawNet(c, e); break;
            case K_RADIO: drawRadio(c, e); break;
            case K_PRAYER: drawPrayerTile(c, e); break;
            case K_FAV: drawWideAppTile(c, e, 68, 30); break;
            case K_MORE: drawWideAppTile(c, e, 56, 26); break;
            case K_ALL: drawAllTile(c, e); break;
            case K_BTN: drawPanelButton(c, e); break;
            default: break;
        }
        c.restore();
    }

    private void drawSettings(Canvas c, El e) {
        fillTile(c, e, 32, C_TILE, C_TILE_F);
        ring(c, e, 32);
        float cx = e.cx(), cy = e.cy();
        pStroke.setPathEffect(null);
        pStroke.setStrokeCap(Paint.Cap.ROUND);
        pStroke.setStrokeWidth(4);
        pStroke.setColor(C_TXT);
        c.drawCircle(cx, cy, 10, pStroke);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            c.drawLine(cx + cs * 15, cy + sn * 15, cx + cs * 21, cy + sn * 21, pStroke);
        }
        pStroke.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawNet(Canvas c, El e) {
        fillTile(c, e, 32, C_TILE, C_TILE_F);
        ring(c, e, 32);
        int col = netOk ? C_TXT : C_BAD;
        float cx = e.r.right - 36, cy = e.cy() + 10;
        pStroke.setPathEffect(null);
        pStroke.setStrokeCap(Paint.Cap.ROUND);
        pStroke.setStrokeWidth(3.5f);
        pStroke.setColor(col);
        float[] rs = {8, 15, 22};
        for (float rr : rs) {
            tmpR.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(tmpR, -135, 90, false, pStroke);
        }
        pStroke.setStrokeCap(Paint.Cap.BUTT);
        pFill.setColor(col);
        c.drawCircle(cx, cy, 3.5f, pFill);
        text(c, netOk ? "متصل" : "لا يوجد إنترنت", e.r.right - 72, e.cy() + 10, 26, col, Paint.Align.RIGHT, ui);
    }

    private void drawRadio(Canvas c, El e) {
        fillTile(c, e, 32, C_TILE, C_TILE_F);
        ring(c, e, 32);
        int st = host.radioState();
        int col = st == 2 ? C_GOLD : C_TXT;
        float cx = e.r.right - 36, cy = e.cy();
        pStroke.setPathEffect(null);
        pStroke.setStrokeCap(Paint.Cap.ROUND);
        pStroke.setStrokeWidth(3.5f);
        pStroke.setColor(col);
        float[] rs = {13, 21};
        for (float rr : rs) {
            tmpR.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(tmpR, -40, 80, false, pStroke);
            c.drawArc(tmpR, 140, 80, false, pStroke);
        }
        pStroke.setStrokeCap(Paint.Cap.BUTT);
        pFill.setColor(col);
        c.drawCircle(cx, cy, 5, pFill);
        String s = st == 2 ? "إيقاف الإذاعة" : (st == 1 ? "جارٍ الاتصال…" : "إذاعة القرآن");
        text(c, s, e.r.right - 72, cy + 10, 26, col, Paint.Align.RIGHT, ui);
    }

    private void drawPrayerTile(Canvas c, El e) {
        int i = e.idx;
        boolean isNow = i == nowIdx, isNext = i == nextIdx && !isNow;
        fillTile(c, e, 26, isNow ? C_NOW : C_TILE, isNow ? 0xFFFFFFFF : C_TILE_F);
        if (isNext) {
            pStroke.setPathEffect(null);
            pStroke.setStrokeWidth(3);
            pStroke.setColor(C_GOLD);
            c.drawRoundRect(e.r, 26, 26, pStroke);
        }
        ring(c, e, 26);
        float cx = e.cx(), top = e.r.top;
        int main = isNow ? C_DARK : C_TXT;
        String lab = tLabel[i];
        if (lab != null) {
            int lc = isNow ? 0xFF5C6B9A : (isNext ? C_GOLD : C_DIM);
            text(c, lab, cx, top + 34, 22, lc, Paint.Align.CENTER, ui);
        }
        text(c, Prayer.NAMES[i], cx, top + 84, 34, main, Paint.Align.CENTER, serifBold);
        text(c, tFull[i], cx, top + 132, 34, main, Paint.Align.CENTER, uiBold);
    }

    private void drawWideAppTile(Canvas c, El e, float icon, float labelSize) {
        fillTile(c, e, 24, C_TILE, C_TILE_F);
        ring(c, e, 24);
        if (e.app == null) return;
        float ir = e.r.right - 22;
        tmpR.set(ir - icon, e.r.top + 18, ir, e.r.top + 18 + icon);
        c.drawBitmap(e.app.icon, srcR, tmpR, pBmp);
        text(c, e.label, ir, e.r.bottom - 22, labelSize, C_TXT, Paint.Align.RIGHT, ui);
    }

    private void drawAllTile(Canvas c, El e) {
        pFill.setColor(lerp(0x00000000, 0x33FFFFFF, e.f));
        c.drawRoundRect(e.r, 24, 24, pFill);
        pStroke.setStrokeWidth(3);
        pStroke.setColor(lerp(0x99B8C6F0, 0xFFFFFFFF, e.f));
        pStroke.setPathEffect(dash);
        c.drawRoundRect(e.r, 24, 24, pStroke);
        pStroke.setPathEffect(null);
        text(c, "كل التطبيقات", e.cx(), e.cy() - 4, 26, C_TXT, Paint.Align.CENTER, ui);
        text(c, Fmt.digits(AppRepo.apps.size()), e.cx(), e.cy() + 40, 32, C_GOLD, Paint.Align.CENTER, uiBold);
    }

    private void drawPanelButton(Canvas c, El e) {
        fillTile(c, e, 37, C_GOLD, 0xFFFFD36E);
        ring(c, e, 37);
        text(c, "مواقيت اليوم والقبلة", e.cx(), e.cy() + 11, 32, C_DARK, Paint.Align.CENTER, uiBold);
    }

    private void drawPanel(Canvas c) {
        float cx = PX + PW / 2f;
        pFill.setColor(C_PANEL);
        c.drawPath(arch, pFill);
        pStroke.setPathEffect(null);
        pStroke.setStrokeWidth(3);
        pStroke.setColor(0xCCDDE6FF);
        c.drawPath(arch, pStroke);
        c.save();
        c.scale(0.955f, 0.97f, cx, PY + PH);
        pStroke.setStrokeWidth(2);
        pStroke.setColor(0x55DDE6FF);
        c.drawPath(arch, pStroke);
        c.restore();

        int verse = 0xFFEADBA6;
        text(c, "إِنَّ الصَّلَاةَ كَانَتْ", cx, 250, 40, verse, Paint.Align.CENTER, serif);
        text(c, "عَلَى الْمُؤْمِنِينَ", cx, 306, 40, verse, Paint.Align.CENTER, serif);
        text(c, "كِتَابًا مَوْقُوتًا", cx, 362, 40, verse, Paint.Align.CENTER, serif);
        text(c, "النساء ١٠٣", cx, 410, 26, C_DIM, Paint.Align.CENTER, ui);

        text(c, panelLabel, cx, 520, 30, C_DIM, Paint.Align.CENTER, ui);
        text(c, panelName, cx, 614, 84, C_GOLD, Paint.Align.CENTER, serifBold);
        text(c, cdStr, cx, 770, 120, C_TXT, Paint.Align.CENTER, uiBold);
        text(c, panelAdhan, cx, 826, 30, C_DIM, Paint.Align.CENTER, ui);
        text(c, suhoorStr, cx, 872, 28, C_DIM, Paint.Align.CENTER, ui);
    }

    // ---------- كل التطبيقات ----------

    private void drawApps(Canvas c) {
        text(c, "كل التطبيقات", GRID_R, 104, 56, C_TXT, Paint.Align.RIGHT, uiBold);
        text(c, "اضغط مطولاً على التطبيق لإضافته للشاشة الرئيسية", GRID_R, 146, 24, C_DIM, Paint.Align.RIGHT, ui);
        text(c, Fmt.digits(appEls.size()) + " تطبيق", 116, 104, 30, C_GOLD, Paint.Align.LEFT, ui);

        c.save();
        c.clipRect(0, 160, W, H);
        c.translate(0, -scroll);
        for (int i = 0; i < appEls.size(); i++) {
            if (i == af) continue;
            drawGridEl(c, appEls.get(i));
        }
        if (af >= 0 && af < appEls.size()) drawGridEl(c, appEls.get(af));
        c.restore();

        if (appEls.isEmpty()) {
            text(c, "جارٍ تحميل التطبيقات…", W / 2f, H / 2f, 36, C_DIM, Paint.Align.CENTER, ui);
        }
    }

    private void drawGridEl(Canvas c, El e) {
        if (e.r.bottom - scroll < 150 || e.r.top - scroll > H) return;
        begin(c, e);
        fillTile(c, e, 24, C_TILE, C_TILE_F);
        ring(c, e, 24);
        float s = 84;
        float l = e.cx() - s / 2f;
        tmpR.set(l, e.r.top + 20, l + s, e.r.top + 20 + s);
        c.drawBitmap(e.app.icon, srcR, tmpR, pBmp);
        text(c, e.label, e.cx(), e.r.bottom - 26, 24, C_TXT, Paint.Align.CENTER, ui);
        c.restore();
    }

    // ---------- المواقيت والقبلة ----------

    private void drawPrayers(Canvas c) {
        text(c, "مواقيت الصلاة والقبلة", 1804, 112, 56, C_TXT, Paint.Align.RIGHT, uiBold);
        text(c, dateLine, 1804, 292, 28, C_DIM, Paint.Align.RIGHT, ui);

        // الصفوف
        for (int i = 0; i < 6; i++) {
            float top = 320 + i * 108f;
            tmpR.set(944, top, 1804, top + 96);
            boolean isNow = i == nowIdx, isNext = i == nextIdx && !isNow;
            pFill.setColor(isNow ? C_NOW : C_TILE);
            c.drawRoundRect(tmpR, 26, 26, pFill);
            if (isNext) {
                pStroke.setPathEffect(null);
                pStroke.setStrokeWidth(3);
                pStroke.setColor(C_GOLD);
                c.drawRoundRect(tmpR, 26, 26, pStroke);
            }
            int main = isNow ? C_DARK : C_TXT;
            text(c, Prayer.NAMES[i], 1764, top + 64, 44, main, Paint.Align.RIGHT, serifBold);
            text(c, tFull[i], 990, top + 64, 46, main, Paint.Align.LEFT, uiBold);
            if (tLabel[i] != null && (isNow || isNext)) {
                text(c, tLabel[i], 1350, top + 62, 26, isNow ? 0xFF5C6B9A : C_GOLD, Paint.Align.CENTER, ui);
            }
        }

        // البوصلة
        float cx = 508, cy = 560, r = 250;
        pStroke.setPathEffect(null);
        pStroke.setStrokeWidth(4);
        pStroke.setColor(0x88B8C6F0);
        c.drawCircle(cx, cy, r, pStroke);
        pStroke.setStrokeWidth(2);
        pStroke.setColor(0x44B8C6F0);
        c.drawCircle(cx, cy, r - 50, pStroke);
        pStroke.setStrokeWidth(3);
        pStroke.setColor(0x88B8C6F0);
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            c.drawLine(cx + cs * (r - 16), cy + sn * (r - 16), cx + cs * r, cy + sn * r, pStroke);
        }
        text(c, "شمال", cx, cy - r - 24, 28, C_TXT, Paint.Align.CENTER, uiBold);
        text(c, "جنوب", cx, cy + r + 48, 28, C_DIM, Paint.Align.CENTER, ui);
        text(c, "شرق", cx + r + 24, cy + 10, 28, C_DIM, Paint.Align.LEFT, ui);
        text(c, "غرب", cx - r - 24, cy + 10, 28, C_DIM, Paint.Align.RIGHT, ui);

        c.save();
        c.rotate((float) qibla, cx, cy);
        pStroke.setStrokeWidth(8);
        pStroke.setStrokeCap(Paint.Cap.ROUND);
        pStroke.setColor(C_GOLD);
        c.drawLine(cx, cy, cx, cy - r + 70, pStroke);
        pStroke.setStrokeCap(Paint.Cap.BUTT);
        pFill.setColor(C_GOLD);
        tmpR.set(cx - 24, cy - r + 14, cx + 24, cy - r + 62);
        c.drawRoundRect(tmpR, 8, 8, pFill);
        pFill.setColor(C_DARK);
        c.drawRect(cx - 24, cy - r + 28, cx + 24, cy - r + 36, pFill);
        c.restore();
        pFill.setColor(C_TXT);
        c.drawCircle(cx, cy, 14, pFill);

        text(c, "اتجاه القبلة من " + city.name, cx, 905, 30, C_DIM, Paint.Align.CENTER, ui);
        text(c, Fmt.digits(Math.round(qibla)) + "° " + Prayer.compass(qibla), cx, 962, 48, C_GOLD, Paint.Align.CENTER, uiBold);
        text(c, "الشمال لأعلى الشاشة — استخدم بوصلة الموبايل للتأكد", cx, 1012, 22, C_DIM, Paint.Align.CENTER, ui);

        // الأزرار
        for (int i = 0; i < prEls.size(); i++) if (i != pf) drawPrEl(c, prEls.get(i));
        if (pf >= 0 && pf < prEls.size()) drawPrEl(c, prEls.get(pf));
    }

    private void drawPrEl(Canvas c, El e) {
        begin(c, e);
        fillTile(c, e, 33, C_TILE, C_TILE_F);
        ring(c, e, 33);
        switch (e.kind) {
            case K_BACK:
                text(c, "رجوع", e.cx(), e.cy() + 11, 32, C_TXT, Paint.Align.CENTER, uiBold);
                break;
            case K_CITY:
                text(c, "المدينة: " + city.name, e.r.right - 26, e.cy() + 11, 30, C_TXT, Paint.Align.RIGHT, ui);
                break;
            case K_HADJ:
                String a = hadj == 0 ? "٠" : (hadj > 0 ? "+" + Fmt.digits(hadj) : "-" + Fmt.digits(-hadj));
                text(c, "ضبط الهجري: " + a + " يوم", e.r.right - 26, e.cy() + 11, 30, C_TXT, Paint.Align.RIGHT, ui);
                break;
            default:
                break;
        }
        c.restore();
    }

    // ---------------- التحكم ----------------

    private int findNext(ArrayList<El> l, int cur, int dir) {
        if (l.isEmpty()) return -1;
        if (cur < 0 || cur >= l.size()) return 0;
        El c = l.get(cur);
        float cx = c.cx(), cy = c.cy();
        int best = -1;
        float bs = Float.MAX_VALUE;
        for (int i = 0; i < l.size(); i++) {
            if (i == cur) continue;
            El e = l.get(i);
            float dx = e.cx() - cx, dy = e.cy() - cy;
            boolean ok;
            float prim, sec;
            switch (dir) {
                case 0: ok = dx < -4; prim = -dx; sec = Math.abs(dy); break;
                case 2: ok = dx > 4; prim = dx; sec = Math.abs(dy); break;
                case 1: ok = dy < -4; prim = -dy; sec = Math.abs(dx); break;
                default: ok = dy > 4; prim = dy; sec = Math.abs(dx); break;
            }
            if (!ok) continue;
            float s = prim + 2.5f * sec;
            if (s < bs) {
                bs = s;
                best = i;
            }
        }
        return best;
    }

    /** dir: 0 يسار، 1 فوق، 2 يمين، 3 تحت */
    private boolean move(int dir) {
        if (mode == HOME) {
            int n = findNext(homeEls, hf, dir);
            if (n >= 0) {
                hf = n;
                kick();
            }
        } else if (mode == APPS) {
            moveApps(dir);
        } else {
            El f = pf >= 0 && pf < prEls.size() ? prEls.get(pf) : null;
            if (f != null && (dir == 0 || dir == 2) && f.kind == K_HADJ) {
                hadj = Math.max(-2, Math.min(2, hadj + (dir == 0 ? 1 : -1)));
                AppRepo.prefs().edit().putInt("hadj", hadj).apply();
                dirty = true;
                tick();
                kick();
            } else if (f != null && (dir == 0 || dir == 2) && f.kind == K_CITY) {
                int n = City.ALL.length;
                cityIdx = (cityIdx + (dir == 0 ? 1 : n - 1)) % n;
                saveCity();
            } else {
                int n = findNext(prEls, pf, dir);
                if (n >= 0) {
                    pf = n;
                    kick();
                }
            }
        }
        return true;
    }

    private void saveCity() {
        AppRepo.prefs().edit().putInt("city", cityIdx).apply();
        applyCity();
        tick();
        kick();
    }

    private void moveApps(int dir) {
        int n = appEls.size();
        if (n == 0) return;
        int t = af;
        switch (dir) {
            case 0:
                if (af % COLS < COLS - 1 && af + 1 < n) t = af + 1;
                break;
            case 2:
                if (af % COLS > 0) t = af - 1;
                break;
            case 1:
                if (af - COLS >= 0) t = af - COLS;
                break;
            default:
                if (af + COLS < n) t = af + COLS;
                else if (af / COLS < (n - 1) / COLS) t = n - 1;
                break;
        }
        if (t != af) {
            af = t;
            ensureVisible();
            kick();
        }
    }

    private float maxScroll() {
        if (appEls.isEmpty()) return 0;
        float bottom = appEls.get(appEls.size() - 1).r.bottom;
        return Math.max(0, bottom - VIEW_BOT + 10);
    }

    private void ensureVisible() {
        if (af < 0 || af >= appEls.size()) return;
        El e = appEls.get(af);
        if (e.idx < COLS) {
            scrollTarget = 0;
            return;
        }
        if (e.r.top - scrollTarget < VIEW_TOP) scrollTarget = e.r.top - VIEW_TOP;
        else if (e.r.bottom - scrollTarget > VIEW_BOT) scrollTarget = e.r.bottom - VIEW_BOT;
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
    }

    private static boolean isCenter(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent ev) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return move(0);
            case KeyEvent.KEYCODE_DPAD_UP: return move(1);
            case KeyEvent.KEYCODE_DPAD_RIGHT: return move(2);
            case KeyEvent.KEYCODE_DPAD_DOWN: return move(3);
            default:
                if (isCenter(k)) {
                    if (ev.getRepeatCount() == 0) ev.startTracking();
                    return true;
                }
                return super.onKeyDown(k, ev);
        }
    }

    @Override
    public boolean onKeyLongPress(int k, KeyEvent ev) {
        if (isCenter(k)) {
            longPress();
            return true;
        }
        return super.onKeyLongPress(k, ev);
    }

    @Override
    public boolean onKeyUp(int k, KeyEvent ev) {
        if (isCenter(k)) {
            if (ev.isTracking() && !ev.isCanceled()) activate(currentEl());
            return true;
        }
        return super.onKeyUp(k, ev);
    }

    private El currentEl() {
        ArrayList<El> l = cur();
        int i = curFocus();
        return (i >= 0 && i < l.size()) ? l.get(i) : null;
    }

    private void activate(El e) {
        if (e == null) return;
        switch (e.kind) {
            case K_SETTINGS: host.openSystem(Settings.ACTION_SETTINGS); break;
            case K_NET: host.openSystem(Settings.ACTION_WIFI_SETTINGS); break;
            case K_RADIO: host.toggleRadio(); kick(); break;
            case K_PRAYER:
            case K_BTN: setMode(PRAYERS); break;
            case K_FAV:
            case K_MORE:
            case K_APP: if (e.app != null) host.launch(e.app); break;
            case K_ALL: setMode(APPS); break;
            case K_BACK: goHome(); break;
            case K_CITY: showCityDialog(); break;
            default: break;
        }
    }

    private AlertDialog.Builder dialog(String title) {
        return new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title);
    }

    private void showCityDialog() {
        String[] names = new String[City.ALL.length];
        for (int i = 0; i < names.length; i++) names[i] = City.ALL[i].name;
        dialog("اختر المدينة").setSingleChoiceItems(names, cityIdx, (d, which) -> {
            cityIdx = which;
            saveCity();
            d.dismiss();
        }).show();
    }

    private void longPress() {
        final El e = currentEl();
        if (e == null || e.app == null) return;
        final AppItem a = e.app;
        if (e.kind == K_FAV || e.kind == K_MORE) {
            final String key = e.kind == K_FAV ? AppRepo.KEY_FAVS : AppRepo.KEY_MORE;
            String[] items = {"إزالة من الشاشة الرئيسية", "معلومات التطبيق", "إلغاء"};
            dialog(a.label).setItems(items, (d, w) -> {
                if (w == 0) {
                    AppRepo.removeFrom(key, a.pkg);
                    rebuildHome();
                    kick();
                } else if (w == 1) {
                    host.appInfo(a.pkg);
                }
            }).show();
        } else if (e.kind == K_APP) {
            String[] items = {"إضافة إلى المفضلة", "إضافة إلى صف (المزيد)", "معلومات التطبيق / إلغاء التثبيت", "إلغاء"};
            dialog(a.label).setItems(items, (d, w) -> {
                if (w == 0) {
                    AppRepo.addTo(AppRepo.KEY_FAVS, a.pkg);
                    rebuildHome();
                    host.toast("تمت الإضافة إلى المفضلة");
                } else if (w == 1) {
                    AppRepo.addTo(AppRepo.KEY_MORE, a.pkg);
                    rebuildHome();
                    host.toast("تمت الإضافة إلى صف المزيد");
                } else if (w == 2) {
                    host.appInfo(a.pkg);
                }
            }).show();
        }
    }

    // ---------------- لمس / ماوس هوائي ----------------

    private float downY, lastY;
    private boolean dragged;

    private int hit(float vx, float vy) {
        float y = mode == APPS ? vy + scroll : vy;
        if (mode == APPS && vy < 160) return -1;
        ArrayList<El> l = cur();
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i).r.contains(vx, y)) return i;
        }
        return -1;
    }

    private void setFocus(int i) {
        if (i < 0 || i == curFocus()) return;
        if (mode == HOME) hf = i;
        else if (mode == APPS) af = i;
        else pf = i;
        kick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float vx = (ev.getX() - ox) / sc, vy = (ev.getY() - oy) / sc;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = lastY = ev.getY();
                dragged = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == APPS) {
                    if (Math.abs(ev.getY() - downY) > 14) dragged = true;
                    if (dragged) {
                        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - (ev.getY() - lastY) / sc));
                        scroll = scrollTarget;
                        invalidate();
                    }
                    lastY = ev.getY();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragged) {
                    int i = hit(vx, vy);
                    if (i >= 0) {
                        setFocus(i);
                        activate(currentEl());
                    }
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_HOVER_MOVE || a == MotionEvent.ACTION_HOVER_ENTER) {
            setFocus(hit((ev.getX() - ox) / sc, (ev.getY() - oy) / sc));
            return true;
        }
        if (a == MotionEvent.ACTION_SCROLL && mode == APPS) {
            float v = ev.getAxisValue(MotionEvent.AXIS_VSCROLL);
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - v * 140f));
            kick();
            return true;
        }
        return super.onGenericMotionEvent(ev);
    }
}
