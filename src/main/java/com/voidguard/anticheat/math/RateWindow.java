package com.voidguard.anticheat.math;

import java.util.ArrayDeque;

/** Kayan zaman penceresi sayacı. Bellek kullanımı 'cap' ile sınırlıdır. */
public final class RateWindow {
    private final ArrayDeque<Long> times = new ArrayDeque<>();
    private final long windowMs;
    private final int cap;

    public RateWindow(long windowMs, int cap) {
        this.windowMs = windowMs;
        this.cap = Math.max(1, cap);
    }

    public int hit(long now) {
        prune(now);
        if (times.size() >= cap) times.pollFirst();
        times.addLast(now);
        return times.size();
    }

    public int count(long now) {
        prune(now);
        return times.size();
    }

    public void clear() {
        times.clear();
    }

    private void prune(long now) {
        while (!times.isEmpty() && now - times.peekFirst() > windowMs) times.pollFirst();
    }
}
