package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.MoveCtx;
import com.voidguard.anticheat.data.PlayerData;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/** Her hile kontrolünün tabanı. Her kontrol ayrı sınıftır ve config.yml'den açılıp kapatılır. */
public abstract class Check {
    protected final VoidGuard plugin;
    private final String name;
    private final String key;
    protected volatile ConfigurationSection cfg;
    private volatile boolean enabled;
    private volatile double weight = 1.0;
    private int errors;

    protected Check(VoidGuard plugin, String name, String key) {
        this.plugin = plugin;
        this.name = name;
        this.key = key;
    }

    public final void reload() {
        cfg = plugin.getConfig().getConfigurationSection("checks." + key);
        enabled = plugin.getConfig().getBoolean("enabled", true) && cfg != null && cfg.getBoolean("enabled", true);
        weight = cfg == null ? 1.0 : cfg.getDouble("weight", 1.0);
        errors = 0;
        onReload();
    }

    protected void onReload() {}

    public final String getName() {
        return name;
    }

    public final String getKey() {
        return key;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    /** Tekrarlayan hatalarda kontrol kendini devre dışı bırakır (sunucuyu loglarla boğmamak için). */
    final boolean recordError() {
        if (++errors >= 25) {
            enabled = false;
            return true;
        }
        return false;
    }

    protected final double d(String path, double def) {
        ConfigurationSection c = cfg;
        return c == null ? def : c.getDouble(path, def);
    }

    protected final int i(String path, int def) {
        ConfigurationSection c = cfg;
        return c == null ? def : c.getInt(path, def);
    }

    protected final boolean b(String path, boolean def) {
        ConfigurationSection c = cfg;
        return c == null ? def : c.getBoolean(path, def);
    }

    protected final void flag(Player p, double amount, String evidence) {
        plugin.violations().flag(p, this, amount * weight, evidence);
    }

    // --- olay kancaları (yalnızca ilgili kontroller geçersiz kılar) ---
    public void onMove(MoveCtx c) {}
    public void onAttack(Player p, PlayerData d, EntityDamageByEntityEvent e) {}
    public void onSwing(Player p, PlayerData d) {}
    public void onBlockPlace(Player p, PlayerData d, BlockPlaceEvent e) {}
    public void onBlockDamage(Player p, PlayerData d, BlockDamageEvent e) {}
    public void onBlockBreak(Player p, PlayerData d, BlockBreakEvent e) {}
    public void onInventoryClick(Player p, PlayerData d, InventoryClickEvent e) {}
    public void onInventoryOpen(Player p, PlayerData d, InventoryOpenEvent e) {}
    public void onInventoryClose(Player p, PlayerData d, InventoryCloseEvent e) {}
    public void onVelocity(Player p, PlayerData d, PlayerVelocityEvent e) {}
    public void onDamage(Player p, PlayerData d, EntityDamageEvent e) {}
    public void onAction(Player p, PlayerData d, String type) {}
    public void onQuit(UUID id) {}
}
