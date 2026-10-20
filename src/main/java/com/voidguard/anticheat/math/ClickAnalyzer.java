package com.voidguard.anticheat.math;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Tıklama zaman damgalarından CPS ve düzenlilik (standart sapma) hesaplar. */
public final class ClickAnalyzer {
    private final ArrayDeque<Long> times = new ArrayDeque<>();
    private final int maxSamples;

    public ClickAnalyzer(int maxSamples) {
        this.maxSamples = Math.max(4, maxSamples);
    }

    public void add(long nowMs) {
        if (times.size() > maxSamples) times.pollFirst();
        times.addLast(nowMs);
    }

    public int cps(long nowMs, long windowMs) {
        int c = 0;
        for (Iterator<Long> it = times.descendingIterator(); it.hasNext(); ) {
            if (nowMs - it.next() <= windowMs) c++; else break;
        }
        return c;
    }

    /** Ardışık tıklama aralıklarının sayısı. */
    public int intervalCount() {
        return Math.max(0, times.size() - 1);
    }

    private double[] intervals() {
        double[] r = new double[Math.max(0, times.size() - 1)];
        long prev = -1;
        int i = 0;
        for (long t : times) {
            if (prev >= 0) r[i++] = t - prev;
            prev = t;
        }
        return r;
    }

    public double stdDev() {
        double[] iv = intervals();
        return MoveMath.stdDev(iv, iv.length);
    }

    public double meanInterval() {
        double[] iv = intervals();
        if (iv.length == 0) return Double.MAX_VALUE;
        double s = 0;
        for (double d : iv) s += d;
        return s / iv.length;
    }

    public void clear() {
        times.clear();
    }
}
