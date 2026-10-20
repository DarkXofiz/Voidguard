package com.voidguard.anticheat.listeners;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.MoveCtx;
import com.voidguard.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class MovementListener implements Listener {
    private final VoidGuard plugin;

    public MovementListener(VoidGuard plugin) {
        this.plugin = plugin;
    }

    /** Ceza animasyonu sırasında konumu sabitler (bakış serbest). Animasyon bitince/çıkışta otomatik kalkar. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFreeze(PlayerMoveEvent e) {
        if (!plugin.effect().isFrozen(e.getPlayer().getUniqueId())) return;
        Location f = e.getFrom();
        Location t = e.getTo();
        if (t == null) return;
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
            Location n = f.clone();
            n.setYaw(t.getYaw());
            n.setPitch(t.getPitch());
            e.setTo(n);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location from = e.getFrom();
        Location to = e.getTo();
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();
        if (to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            d.lastMoveTime = now;
            return;
        }
        // Özel hareket durumları: elytra, riptide, araç, uçuş izni değişimi → sonrasında kısa tolerans
        if (p.isGliding() || p.isRiptiding() || p.isInsideVehicle() || p.isFlying()) d.lastSpecialMove = now;
        boolean af = p.getAllowFlight();
        if (d.lastAllowFlight && !af) d.lastFlightChange = now;
        d.lastAllowFlight = af;

        if (!plugin.effect().isActive(p.getUniqueId())) {
            plugin.checks().onMove(new MoveCtx(plugin, p, d, from, to, now));
        }
        d.lastMoveTime = now;
    }
}
