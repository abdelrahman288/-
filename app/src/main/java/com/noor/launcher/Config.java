package com.noor.launcher;

/** إعدادات سهلة التعديل. */
final class Config {
    private Config() {}

    /** رابط بث إذاعة القرآن الكريم. لو مش شغال، بدّله بأي رابط بث MP3/AAC مباشر. */
    static final String RADIO_URL = "https://stream.radiojar.com/8s5u5tpdtwzuv";

    /** ضبط يدوي للمواقيت بالدقائق (الفجر، الشروق، الظهر، العصر، المغرب، العشاء). مثال: {0,0,0,0,1,0} */
    static final int[] OFFSET_MIN = {0, 0, 1, 0, 0, 0};

    static final int MAX_FAVS = 4;
    static final int MAX_MORE = 5;
}
