package com.voidguard.anticheat.listeners;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.punishments.BanRecord;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/** Giriş/çıkış, ban doğrulama ve hareket muafiyetlerini besleyen durum olayları. */
public final class ConnectionListener implements Listener {
    private final VoidGuard plugin;

    public ConnectionListener(VoidGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation")
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        BanRecord b = plugin.punishments().getActiveBan(e.getUniqueId());
        if (b != null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.punishments().banScreen(b));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.data().get(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        plugin.effect().onQuit(id);       // efekt sürüyorsa ceza yine uygulanır
        plugin.checks().onQuit(id);
        plugin.violations().forget(id);
        plugin.data().remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        plugin.data().get(e.getPlayer()).lastTeleport = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        plugin.data().get(e.getPlayer()).lastTeleport = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        plugin.data().get(e.getPlayer()).lastTeleport = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        plugin.data().get(e.getPlayer()).lastGamemode = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFlight(PlayerToggleFlightEvent e) {
        plugin.data().get(e.getPlayer()).lastFlightChange = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        d.velocityCounter++;
        plugin.checks().onVelocity(p, d, e); // kontroller önceki durumu görsün diye önce dağıt
        d.lastVelocity = System.currentTimeMillis();
    }
}
