package com.voidguard.anticheat.util;

/** Bukkit'ten bağımsız muafiyet kararları (test edilebilir). */
public final class ExemptionPolicy {
    private ExemptionPolicy() {}

    public static final class MovementState {
        private final boolean creativeOrSpectator;
        private final boolean allowFlight;
        private final boolean flying;
        private final boolean gliding;
        private final boolean riptiding;
        private final boolean inVehicle;
        private final boolean dead;
        private final boolean sleeping;
        private final long msSinceTeleport;
        private final long msSinceVelocity;
        private final long msSinceGamemode;
        private final long msSinceSpecialMove;
        private final boolean effectActive;
        public MovementState(boolean creativeOrSpectator, boolean allowFlight, boolean flying, boolean gliding, boolean riptiding, boolean inVehicle, boolean dead, boolean sleeping, long msSinceTeleport, long msSinceVelocity, long msSinceGamemode, long msSinceSpecialMove, boolean effectActive) {
            this.creativeOrSpectator = creativeOrSpectator;
            this.allowFlight = allowFlight;
            this.flying = flying;
            this.gliding = gliding;
            this.riptiding = riptiding;
            this.inVehicle = inVehicle;
            this.dead = dead;
            this.sleeping = sleeping;
            this.msSinceTeleport = msSinceTeleport;
            this.msSinceVelocity = msSinceVelocity;
            this.msSinceGamemode = msSinceGamemode;
            this.msSinceSpecialMove = msSinceSpecialMove;
            this.effectActive = effectActive;
        }
        public boolean creativeOrSpectator() { return creativeOrSpectator; }
        public boolean allowFlight() { return allowFlight; }
        public boolean flying() { return flying; }
        public boolean gliding() { return gliding; }
        public boolean riptiding() { return riptiding; }
        public boolean inVehicle() { return inVehicle; }
        public boolean dead() { return dead; }
        public boolean sleeping() { return sleeping; }
        public long msSinceTeleport() { return msSinceTeleport; }
        public long msSinceVelocity() { return msSinceVelocity; }
        public long msSinceGamemode() { return msSinceGamemode; }
        public long msSinceSpecialMove() { return msSinceSpecialMove; }
        public boolean effectActive() { return effectActive; }
    }

    public static final class Grace {
        private final long teleportMs;
        private final long velocityMs;
        private final long gamemodeMs;
        private final long specialMs;
        public Grace(long teleportMs, long velocityMs, long gamemodeMs, long specialMs) {
            this.teleportMs = teleportMs;
            this.velocityMs = velocityMs;
            this.gamemodeMs = gamemodeMs;
            this.specialMs = specialMs;
        }
        public long teleportMs() { return teleportMs; }
        public long velocityMs() { return velocityMs; }
        public long gamemodeMs() { return gamemodeMs; }
        public long specialMs() { return specialMs; }
    }

    /** true ise hareket tabanlı kontroller bu oyuncuyu bu olayda atlar. */
    public static boolean movementExempt(MovementState s, Grace g) {
        if (s.creativeOrSpectator() || s.allowFlight() || s.flying()) return true;
        if (s.gliding() || s.riptiding() || s.inVehicle()) return true;
        if (s.dead() || s.sleeping() || s.effectActive()) return true;
        return s.msSinceTeleport() < g.teleportMs()
                || s.msSinceVelocity() < g.velocityMs()
                || s.msSinceGamemode() < g.gamemodeMs()
                || s.msSinceSpecialMove() < g.specialMs();
    }

    /** Sunucu lag'ı veya yüksek ping varsa kontroller atlanır (yanlış pozitifi önler). */
    public static boolean shouldSkipForLoad(double tps, double minTps, int ping, int maxPing, boolean lagging) {
        return lagging || tps < minTps || ping > maxPing;
    }
}
