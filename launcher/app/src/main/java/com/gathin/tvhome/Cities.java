package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Saudi cities the clean view can be set to (TV pill → «الموقع», or "city" in a theme): the name shown top left,
 * and the place prayer times and the weather are worked out for. All are in Asia/Riyadh.
 */
final class Cities {

    static final class City {
        final String name;
        final double latitude, longitude;

        City(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    static final City DEFAULT = new City("المدينة المنورة", 24.4686, 39.6142);

    static final List<City> ALL = Collections.unmodifiableList(Arrays.asList(
            DEFAULT,
            new City("مكة المكرمة", 21.4225, 39.8262),
            new City("الرياض", 24.7136, 46.6753),
            new City("جدة", 21.4858, 39.1925),
            new City("الدمام", 26.4207, 50.0888),
            new City("الخبر", 26.2172, 50.1971),
            new City("الأحساء", 25.3833, 49.5833),
            new City("الجبيل", 27.0174, 49.6225),
            new City("الطائف", 21.2703, 40.4158),
            new City("ينبع", 24.0895, 38.0618),
            new City("العلا", 26.6085, 37.9232),
            new City("خيبر", 25.6981, 39.2919),
            new City("بدر", 23.7800, 38.7900),
            new City("تبوك", 28.3835, 36.5662),
            new City("بريدة", 26.3260, 43.9750),
            new City("حائل", 27.5114, 41.7208),
            new City("أبها", 18.2164, 42.5053),
            new City("خميس مشيط", 18.3000, 42.7333),
            new City("الباحة", 20.0129, 41.4677),
            new City("جازان", 16.8892, 42.5511),
            new City("نجران", 17.4924, 44.1277),
            new City("عرعر", 30.9753, 41.0381),
            new City("سكاكا", 29.9697, 40.2064)));

    private Cities() {
    }

    /** The city called {@code name}, or null. */
    static City find(String name) {
        String n = name == null ? "" : name.trim();
        for (City c : ALL) if (c.name.equals(n)) return c;
        return null;
    }
}
