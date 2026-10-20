package com.voidguard.anticheat.listeners;

import com.voidguard.anticheat.VoidGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/** Paket kontrolü için oran sayacı olayları (hotbar, eşya atma, etkileşim, eğilme). */
public final class ActionListener implements Listener {
    private final VoidGuard plugin;

    public ActionListener(VoidGuard plugin) {
        this.plugin = plugin;
    }

    private void fire(Player p, String type) {
        plugin.checks().onAction(p, plugin.data().get(p), type);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent e) {
        fire(e.getPlayer(), "HELD");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent e) {
        fire(e.getPlayer(), "DROP");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        fire(e.getPlayer(), "INTERACT");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent e) {
        fire(e.getPlayer(), "SNEAK");
    }
}
