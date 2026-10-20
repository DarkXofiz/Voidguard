package com.voidguard.anticheat.punishments;

import java.util.HashMap;
import java.util.Map;

/**
 * Oyuncu başına, kontrol bazlı "doğrulanmış tespit" sayacı (Bukkit'ten bağımsız, test edilebilir).
 * Aynı olayın art arda tekrar işlenmesi sayıyı artırmaz (min aralık); uzun süre tespit gelmezse sayaç sıfırlanır.
 */
public final class DetectionLedger {
    public enum Result { COUNTED, DUPLICATE, THRESHOLD }

    public static final class Outcome {
        private final Result result;
        private final int count;
        Outcome(Result result, int count) {
            this.result = result;
            this.count = count;
        }
        public Result result() { return result; }
        /** Bu çağrıdan sonraki tespit sayısı (THRESHOLD'da eşiğe ulaşılan değer). */
        public int count() { return count; }
        public boolean counted() { return result != Result.DUPLICATE; }
    }

    private static final class Entry {
        int count;
        long last;
    }

    private final Map<String, Entry> map = new HashMap<>();

    public synchronized Outcome record(String check, long now, long minIntervalMs, long windowMs, int required) {
        Entry e = map.computeIfAbsent(check, k -> new Entry());
        if (e.count > 0 && now - e.last > windowMs) e.count = 0; // süre doldu: eski tespitler geçersiz
        if (e.count > 0 && now - e.last < minIntervalMs) return new Outcome(Result.DUPLICATE, e.count);
        e.count++;
        e.last = now;
        if (e.count >= Math.max(1, required)) {
            int reached = e.count;
            e.count = 0; // eşik tamamlandı; ceza tetiklenir
            return new Outcome(Result.THRESHOLD, reached);
        }
        return new Outcome(Result.COUNTED, e.count);
    }

    public synchronized int count(String check, long now, long windowMs) {
        Entry e = map.get(check);
        return e == null || now - e.last > windowMs ? 0 : e.count;
    }

    public synchronized boolean idle(long now, long windowMs) {
        for (Entry e : map.values()) if (e.count > 0 && now - e.last <= windowMs) return false;
        return true;
    }

    public synchronized void clear() {
        map.clear();
    }
}
