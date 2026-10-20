package com.voidguard.anticheat.data;

import com.voidguard.anticheat.punishments.ViolationTracker;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Oyuncu başına ortak durum. Yalnızca ana iş parçacığından değiştirilir. Oyuncu çıkışında silinir. */
public final class PlayerData {
    public final UUID uuid;
    public volatile String name;
    public final ViolationTracker tracker;

    public long joinTime;
    public long lastTeleport;
    public long lastVelocity;
    public long lastGamemode;
    public long lastFlightChange;
    public long lastDamage;
    public long lastBlockDamage;
    public long lastSpecialMove;
    public long lastMoveTime;
    public int velocityCounter;
    public boolean lastAllowFlight;

    public long exemptCheckedAt;
    public boolean exemptCached;

    public final Map<String, Long> alertCooldown = new HashMap<>();
    public final Map<String, Long> logCooldown = new HashMap<>();

    public PlayerData(UUID uuid, String name, int evidenceMax) {
        this.uuid = uuid;
        this.name = name;
        this.tracker = new ViolationTracker(evidenceMax);
        long now = System.currentTimeMillis();
        this.joinTime = now;
        this.lastTeleport = now; // girişte kısa süreli tolerans
    }
}
