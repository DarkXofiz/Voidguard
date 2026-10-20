package com.voidguard.anticheat.listeners;

import com.voidguard.anticheat.VoidGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockListener implements Listener {
    private final VoidGuard plugin;

    public BlockListener(VoidGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        plugin.checks().onBlockPlace(p, plugin.data().get(p), e);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(BlockDamageEvent e) {
        Player p = e.getPlayer();
        plugin.checks().onBlockDamage(p, plugin.data().get(p), e);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        plugin.checks().onBlockBreak(p, plugin.data().get(p), e);
    }
}
