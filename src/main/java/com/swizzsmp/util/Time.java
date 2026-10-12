package com.swizzsmp.util;

public final class Time {

    private Time() {
    }

    /** Formats milliseconds as "1:42" (one minute or more) or "8s". Always rounds up. */
    public static String format(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000);
        if (totalSeconds >= 60) {
            return (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
        }
        return totalSeconds + "s";
    }

    public static long secondsToMillis(double seconds) {
        return Math.round(seconds * 1000.0);
    }

    public static int secondsToTicks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }
}
