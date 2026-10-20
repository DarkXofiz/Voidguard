package com.voidguard.anticheat.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class TimeUtil {
    private TimeUtil() {}

    /** "30m", "12h", "7d", "2w", "perm" biçimlerini ms'ye çevirir. 0 = kalıcı. Geçersizse -1. */
    public static long parseDuration(String s) {
        if (s == null) return -1;
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.equals("perm") || s.equals("permanent") || s.equals("kalici") || s.equals("0")) return 0;
        if (s.length() < 2) return -1;
        char unit = s.charAt(s.length() - 1);
        long n;
        try {
            n = Long.parseLong(s.substring(0, s.length() - 1));
        } catch (NumberFormatException e) {
            return -1;
        }
        if (n <= 0) return -1;
        switch (unit) {
            case 's': return TimeUnit.SECONDS.toMillis(n);
            case 'm': return TimeUnit.MINUTES.toMillis(n);
            case 'h': return TimeUnit.HOURS.toMillis(n);
            case 'd': return TimeUnit.DAYS.toMillis(n);
            case 'w': return TimeUnit.DAYS.toMillis(n * 7);
            default: return -1;
        }
    }

    public static String format(long ms) {
        if (ms <= 0) return "Kalıcı";
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(" gün ");
        if (h > 0) sb.append(h).append(" saat ");
        if (m > 0) sb.append(m).append(" dk ");
        if (sb.length() == 0) sb.append(s).append(" sn");
        return sb.toString().trim();
    }
}
