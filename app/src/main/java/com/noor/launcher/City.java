package com.noor.launcher;

/** مدينة لحساب المواقيت. method: 0 = الهيئة المصرية (فجر 19.5 / عشاء 17.5)، 1 = أم القرى (فجر 18.5 / عشاء بعد المغرب 90 دقيقة). */
final class City {
    final String name;
    final double lat, lng;
    final String zone;
    final int method;

    City(String name, double lat, double lng, String zone, int method) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.zone = zone;
        this.method = method;
    }

    static final City[] ALL = {
            new City("القاهرة", 30.0444, 31.2357, "Africa/Cairo", 0),
            new City("الجيزة", 30.0131, 31.2089, "Africa/Cairo", 0),
            new City("الإسكندرية", 31.2001, 29.9187, "Africa/Cairo", 0),
            new City("المنصورة", 31.0364, 31.3807, "Africa/Cairo", 0),
            new City("طنطا", 30.7865, 31.0004, "Africa/Cairo", 0),
            new City("الزقازيق", 30.5877, 31.5020, "Africa/Cairo", 0),
            new City("بورسعيد", 31.2653, 32.3019, "Africa/Cairo", 0),
            new City("السويس", 29.9668, 32.5498, "Africa/Cairo", 0),
            new City("الفيوم", 29.3084, 30.8428, "Africa/Cairo", 0),
            new City("المنيا", 28.1099, 30.7503, "Africa/Cairo", 0),
            new City("أسيوط", 27.1783, 31.1859, "Africa/Cairo", 0),
            new City("سوهاج", 26.5569, 31.6948, "Africa/Cairo", 0),
            new City("الأقصر", 25.6872, 32.6396, "Africa/Cairo", 0),
            new City("أسوان", 24.0889, 32.8998, "Africa/Cairo", 0),
            new City("الغردقة", 27.2579, 33.8116, "Africa/Cairo", 0),
            new City("شرم الشيخ", 27.9158, 34.3300, "Africa/Cairo", 0),
            new City("مكة المكرمة", 21.3891, 39.8579, "Asia/Riyadh", 1),
            new City("المدينة المنورة", 24.5247, 39.5692, "Asia/Riyadh", 1),
            new City("الرياض", 24.7136, 46.6753, "Asia/Riyadh", 1),
            new City("جدة", 21.4858, 39.1925, "Asia/Riyadh", 1),
            new City("الكويت", 29.3759, 47.9774, "Asia/Kuwait", 1),
            new City("الدوحة", 25.2854, 51.5310, "Asia/Qatar", 1),
            new City("دبي", 25.2048, 55.2708, "Asia/Dubai", 1),
            new City("بغداد", 33.3152, 44.3661, "Asia/Baghdad", 0),
            new City("عمّان", 31.9454, 35.9284, "Asia/Amman", 0),
            new City("بيروت", 33.8938, 35.5018, "Asia/Beirut", 0),
    };
}
