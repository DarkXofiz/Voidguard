package com.voidguard.anticheat.util;

import com.voidguard.anticheat.VoidGuard;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Sunucu lag'ı / TPS düşüşü / yüksek ping durumunda kontrolleri atlar. */
public final class PerformanceGate {
    private final VoidGuard plugin;
    private volatile long lagUntil;
    private long lastTick = System.currentTimeMillis();
    private BukkitTask task;
    private volatile double minTps = 17.5;
    private volatile int maxPing = 450;
    private volatile long spikeMs = 1250;

    public PerformanceGate(VoidGuard plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        minTps = c.getDouble("tolerance.min-tps", 17.5);
        maxPing = c.getInt("tolerance.max-ping", 450);
        spikeMs = c.getLong("tolerance.lag-spike-ms", 1250);
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            long gap = now - lastTick;
            lastTick = now;
            if (gap > spikeMs) lagUntil = now + 5000;
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public boolean lagging() {
        return System.currentTimeMillis() < lagUntil;
    }

    /** Kontrollerin neden atlandığını açıklar (teşhis için); atlanmıyorsa null. */
    public String reason(Player p) {
        double tps = ServerUtil.tps();
        int ping = ServerUtil.ping(p);
        if (lagging()) return "lag modu (sunucu gecikmesi algılandı)";
        if (tps < minTps) return "TPS düşük (" + String.format(java.util.Locale.ROOT, "%.1f", tps) + " < " + minTps + ")";
        if (ping > maxPing) return "ping yüksek (" + ping + " > " + maxPing + ")";
        return null;
    }

    public boolean skip(Player p) {
        return ExemptionPolicy.shouldSkipForLoad(ServerUtil.tps(), minTps, ServerUtil.ping(p), maxPing, lagging());
    }
}
