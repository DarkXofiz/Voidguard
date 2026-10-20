package com.voidguard.anticheat.punishments;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Oyuncu başına, kontrol bazlı VL defteri. Zamanla azalır. Bellek: kontrol sayısı x evidenceMax. */
public final class ViolationTracker {
    private static final class Entry {
        double vl;
        long updated;
        final ArrayDeque<String> evidence = new ArrayDeque<>();
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final int evidenceMax;

    public ViolationTracker(int evidenceMax) {
        this.evidenceMax = Math.max(1, evidenceMax);
    }

    private static void decay(Entry e, long now, double perMinute) {
        if (now > e.updated) {
            e.vl = Math.max(0.0, e.vl - perMinute * ((now - e.updated) / 60000.0));
            e.updated = now;
        }
    }

    public synchronized double add(String check, double amount, long now, double decayPerMinute, String evidence) {
        Entry e = map.computeIfAbsent(check, k -> {
            Entry n = new Entry();
            n.updated = now;
            return n;
        });
        decay(e, now, decayPerMinute);
        e.vl += Math.max(0.0, amount);
        if (evidence != null) {
            if (e.evidence.size() >= evidenceMax) e.evidence.pollFirst();
            e.evidence.addLast(evidence);
        }
        return e.vl;
    }

    public synchronized double get(String check, long now, double decayPerMinute) {
        Entry e = map.get(check);
        if (e == null) return 0.0;
        decay(e, now, decayPerMinute);
        return e.vl;
    }

    public synchronized Map<String, Double> snapshot(long now, double decayPerMinute) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> me : map.entrySet()) {
            decay(me.getValue(), now, decayPerMinute);
            if (me.getValue().vl > 0.0) out.put(me.getKey(), me.getValue().vl);
        }
        return out;
    }

    public synchronized List<String> evidence(String check) {
        Entry e = map.get(check);
        return e == null ? new ArrayList<>() : new ArrayList<>(e.evidence);
    }

    public synchronized void clear() {
        map.clear();
    }
}
