package com.noor.launcher;

import java.util.Calendar;
import java.util.TimeZone;

/** حساب مواقيت الصلاة والقبلة أوفلاين (خوارزمية PrayTimes). */
final class Prayer {
    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    private static final double KAABA_LAT = 21.4225, KAABA_LNG = 39.8262;

    private Prayer() {}

    /** يرجّع 6 أوقات (بالـ ms) لليوم المحدد بتوقيت المدينة. */
    static long[] times(int year, int month, int day, City c) {
        TimeZone tz = TimeZone.getTimeZone(c.zone);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(year, month - 1, day, 0, 0, 0);
        long midnight = utc.getTimeInMillis();
        double tzHours = tz.getOffset(midnight + 12L * 3600000L - tz.getRawOffset()) / 3600000.0;

        double jd = julian(year, month, day) - c.lng / 360.0;
        double fajrAngle = c.method == 1 ? 18.5 : 19.5;
        double ishaAngle = 17.5;

        double fajr = angleTime(jd, c.lat, fajrAngle, 5 / 24.0, true);
        double sunrise = angleTime(jd, c.lat, 0.833, 6 / 24.0, true);
        double dhuhr = midDay(jd, 12 / 24.0);
        double asr = asrTime(jd, c.lat, 1, 13 / 24.0);
        double maghrib = angleTime(jd, c.lat, 0.833, 18 / 24.0, false);
        // أم القرى: العشاء بعد المغرب بـ 90 دقيقة
        double isha = c.method == 1 ? maghrib + 1.5 : angleTime(jd, c.lat, ishaAngle, 18 / 24.0, false);

        double[] h = {fajr, sunrise, dhuhr, asr, maghrib, isha};
        long[] out = new long[6];
        for (int i = 0; i < 6; i++) {
            double local = h[i] + tzHours - c.lng / 15.0;
            long minutes = Math.round(local * 60.0) + Config.OFFSET_MIN[i];
            out[i] = midnight + minutes * 60000L - Math.round(tzHours * 3600000.0);
        }
        return out;
    }

    /** اتجاه القبلة بالدرجات من الشمال الحقيقي (مع عقارب الساعة). */
    static double qibla(City c) {
        double p1 = Math.toRadians(c.lat), p2 = Math.toRadians(KAABA_LAT);
        double dl = Math.toRadians(KAABA_LNG - c.lng);
        double y = Math.sin(dl);
        double x = Math.cos(p1) * Math.tan(p2) - Math.sin(p1) * Math.cos(dl);
        double b = Math.toDegrees(Math.atan2(y, x));
        return (b + 360.0) % 360.0;
    }

    static String compass(double deg) {
        String[] n = {"شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"};
        return n[(int) Math.round(deg / 45.0) % 8];
    }

    // ---------------- رياضيات ----------------

    private static double julian(int y, int m, int d) {
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** {الميل الشمسي بالدرجات، معادلة الزمن بالساعات} */
    private static double[] sunPos(double jd) {
        double dd = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * dd);
        double q = fixAngle(280.459 + 0.98564736 * dd);
        double l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * dd;
        double ra = fixHour(Math.toDegrees(Math.atan2(dcos(e) * dsin(l), dcos(l))) / 15.0);
        double eqt = q / 15.0 - ra;
        double decl = Math.toDegrees(Math.asin(dsin(e) * dsin(l)));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double t) {
        return fixHour(12 - sunPos(jd + t)[1]);
    }

    private static double angleTime(double jd, double lat, double angle, double t, boolean ccw) {
        double decl = sunPos(jd + t)[0];
        double noon = midDay(jd, t);
        double v = Math.toDegrees(Math.acos((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat)))) / 15.0;
        return noon + (ccw ? -v : v);
    }

    private static double asrTime(double jd, double lat, double factor, double t) {
        double decl = sunPos(jd + t)[0];
        double cot = Math.toDegrees(Math.atan(1.0 / (factor + Math.tan(Math.toRadians(Math.abs(lat - decl))))));
        return angleTime(jd, lat, -cot, t, false);
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double fixAngle(double a) { return fix(a, 360.0); }
    private static double fixHour(double a) { return fix(a, 24.0); }
    private static double fix(double a, double mode) {
        a = a - mode * Math.floor(a / mode);
        return a < 0 ? a + mode : a;
    }
}
