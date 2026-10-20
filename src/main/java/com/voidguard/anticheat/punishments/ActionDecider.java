package com.voidguard.anticheat.punishments;

import java.util.Map;

/** VL durumuna göre eylem seviyesini belirleyen saf mantık. */
public final class ActionDecider {
    private ActionDecider() {}

    public enum Level { NONE, LOG, ALERT, PUNISH }

    public static final class Thresholds {
        private final double alertVl;
        private final double punishVl;
        private final int minDistinctChecks;
        private final double singleCheckMultiplier;
        public Thresholds(double alertVl, double punishVl, int minDistinctChecks, double singleCheckMultiplier) {
            this.alertVl = alertVl;
            this.punishVl = punishVl;
            this.minDistinctChecks = minDistinctChecks;
            this.singleCheckMultiplier = singleCheckMultiplier;
        }
        public double alertVl() { return alertVl; }
        public double punishVl() { return punishVl; }
        public int minDistinctChecks() { return minDistinctChecks; }
        public double singleCheckMultiplier() { return singleCheckMultiplier; }
    }

    public static final class Decision {
        private final Level level;
        private final boolean highConfidence;
        private final String topCheck;
        private final double topVl;
        private final int strongChecks;
        public Decision(Level level, boolean highConfidence, String topCheck, double topVl, int strongChecks) {
            this.level = level;
            this.highConfidence = highConfidence;
            this.topCheck = topCheck;
            this.topVl = topVl;
            this.strongChecks = strongChecks;
        }
        public Level level() { return level; }
        public boolean highConfidence() { return highConfidence; }
        public String topCheck() { return topCheck; }
        public double topVl() { return topVl; }
        public int strongChecks() { return strongChecks; }
    }

    public static Decision decide(Map<String, Double> vls, Thresholds t) {
        String top = null;
        double topVl = 0.0;
        int strong = 0;
        for (Map.Entry<String, Double> e : vls.entrySet()) {
            double v = e.getValue();
            if (v > topVl) { topVl = v; top = e.getKey(); }
            if (v >= t.punishVl()) strong++;
        }
        if (top == null) return new Decision(Level.NONE, false, null, 0.0, 0);
        if (strong >= Math.max(1, t.minDistinctChecks())) {
            return new Decision(Level.PUNISH, true, top, topVl, strong);
        }
        if (topVl >= t.punishVl() * t.singleCheckMultiplier()) {
            return new Decision(Level.PUNISH, false, top, topVl, strong);
        }
        if (topVl >= t.alertVl()) return new Decision(Level.ALERT, false, top, topVl, strong);
        return new Decision(Level.LOG, false, top, topVl, strong);
    }
}
