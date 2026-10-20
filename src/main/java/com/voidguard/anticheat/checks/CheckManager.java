package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.MoveCtx;
import com.voidguard.anticheat.data.PlayerData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
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

/** Kontrolleri kaydeder ve olayları dağıtır. Bir kontrolün hatası diğerlerini etkilemez. */
public final class CheckManager {
    @FunctionalInterface
    private interface Action {
        void run(Check c) throws Exception;
    }

    private final VoidGuard plugin;
    private final List<Check> checks = new ArrayList<>();

    public CheckManager(VoidGuard plugin) {
        this.plugin = plugin;
        checks.add(new KillAuraCheck(plugin));
        checks.add(new ReachCheck(plugin));
        checks.add(new FlyCheck(plugin));
        checks.add(new SpeedCheck(plugin));
        checks.add(new NoFallCheck(plugin));
        checks.add(new SpiderCheck(plugin));
        checks.add(new JesusCheck(plugin));
        checks.add(new AutoClickerCheck(plugin));
        checks.add(new ScaffoldCheck(plugin));
        checks.add(new AntiKnockbackCheck(plugin));
        checks.add(new FastBreakCheck(plugin));
        checks.add(new InventoryCheck(plugin));
        checks.add(new PacketCheck(plugin));
    }

    public void reload() {
        for (Check c : checks) c.reload();
    }

    public List<Check> all() {
        return Collections.unmodifiableList(checks);
    }

    private final java.util.Map<UUID, Long> skipLogged = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean skip(Player p, PlayerData d) {
        boolean skip = !plugin.getConfig().getBoolean("enabled", true)
                || plugin.exemptions().isExempt(p, d)
                || plugin.gate().skip(p)
                || plugin.effect().isActive(p.getUniqueId());
        if (skip && plugin.getConfig().getBoolean("logging.debug", false)) {
            long now = System.currentTimeMillis();
            Long last = skipLogged.get(p.getUniqueId());
            if (last == null || now - last > 15_000) {
                skipLogged.put(p.getUniqueId(), now);
                String gate = plugin.gate().reason(p);
                plugin.getLogger().info("[DEBUG] " + p.getName() + " denetlenmiyor: "
                        + (plugin.exemptions().computeExempt(p) ? "muaf (OP/izin)" : gate != null ? gate : "efekt/diğer"));
            }
        }
        return skip;
    }

    private void each(Action a) {
        for (Check c : checks) {
            if (!c.isEnabled()) continue;
            try {
                a.run(c);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Kontrol hatası (" + c.getName() + ")", t);
                if (c.recordError()) {
                    plugin.getLogger().severe(c.getName() + " kontrolü tekrarlayan hatalar nedeniyle devre dışı bırakıldı.");
                }
            }
        }
    }

    public void onMove(MoveCtx m) {
        if (skip(m.player, m.data)) return;
        each(c -> c.onMove(m));
    }

    public void onAttack(Player p, PlayerData d, EntityDamageByEntityEvent e) {
        if (skip(p, d)) return;
        each(c -> c.onAttack(p, d, e));
    }

    public void onSwing(Player p, PlayerData d) {
        if (skip(p, d)) return;
        each(c -> c.onSwing(p, d));
    }

    public void onBlockPlace(Player p, PlayerData d, BlockPlaceEvent e) {
        if (skip(p, d)) return;
        each(c -> c.onBlockPlace(p, d, e));
    }

    public void onBlockDamage(Player p, PlayerData d, BlockDamageEvent e) {
        each(c -> c.onBlockDamage(p, d, e)); // durum kaydı: muafiyetten bağımsız
    }

    public void onBlockBreak(Player p, PlayerData d, BlockBreakEvent e) {
        if (skip(p, d)) return;
        each(c -> c.onBlockBreak(p, d, e));
    }

    public void onInventoryClick(Player p, PlayerData d, InventoryClickEvent e) {
        if (skip(p, d)) return;
        each(c -> c.onInventoryClick(p, d, e));
    }

    public void onInventoryOpen(Player p, PlayerData d, InventoryOpenEvent e) {
        each(c -> c.onInventoryOpen(p, d, e));
    }

    public void onInventoryClose(Player p, PlayerData d, InventoryCloseEvent e) {
        each(c -> c.onInventoryClose(p, d, e));
    }

    public void onVelocity(Player p, PlayerData d, PlayerVelocityEvent e) {
        if (skip(p, d)) return;
        each(c -> c.onVelocity(p, d, e));
    }

    public void onDamage(Player p, PlayerData d, EntityDamageEvent e) {
        each(c -> c.onDamage(p, d, e)); // durum kaydı: muafiyetten bağımsız
    }

    public void onAction(Player p, PlayerData d, String type) {
        if (skip(p, d)) return;
        each(c -> c.onAction(p, d, type));
    }

    public void onQuit(UUID id) {
        for (Check c : checks) {
            try {
                c.onQuit(id);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Temizlik hatası (" + c.getName() + ")", t);
            }
        }
    }
}
