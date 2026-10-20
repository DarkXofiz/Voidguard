package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.math.MoveMath;
import com.voidguard.anticheat.util.ServerUtil;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * FastBreak: kazmaya başlama ile kırma arasındaki süre, blok sertliği/alet/Efficiency/Haste'ten hesaplanan
 * muhafazakâr alt sınırdan kısaysa şüphe. Alt sınır her zaman "doğru ve en iyi alet" varsayar (yanlış pozitifi azaltır).
 * Özel (eklenti) aletleri bu varsayımı aşabilir; gerekirse eşik/kapatma config'den ayarlanır.
 */
public final class FastBreakCheck extends Check {
    private static final class S {
        UUID world;
        int x, y, z;
        long start;
        double buf;
    }

    private final Map<UUID, S> states = new HashMap<>();

    public FastBreakCheck(VoidGuard plugin) {
        super(plugin, "FastBreak", "fastbreak");
    }

    private static double toolSpeed(Material m) {
        String n = m.name();
        if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")) {
            if (n.startsWith("NETHERITE_")) return 9;
            if (n.startsWith("DIAMOND_")) return 8;
            if (n.startsWith("IRON_")) return 6;
            if (n.startsWith("STONE_")) return 4;
            if (n.startsWith("GOLDEN_")) return 12;
            if (n.startsWith("WOODEN_")) return 2;
        }
        if (n.equals("SHEARS") || n.endsWith("_SWORD")) return 15;
        return 1;
    }

    @Override
    public void onBlockDamage(Player p, PlayerData d, BlockDamageEvent e) {
        d.lastBlockDamage = System.currentTimeMillis();
        S s = states.computeIfAbsent(p.getUniqueId(), k -> new S());
        Block bl = e.getBlock();
        s.world = bl.getWorld().getUID();
        s.x = bl.getX();
        s.y = bl.getY();
        s.z = bl.getZ();
        s.start = System.currentTimeMillis();
    }

    @Override
    public void onBlockBreak(Player p, PlayerData d, BlockBreakEvent e) {
        if (p.getGameMode() == GameMode.CREATIVE) return;
        Block bl = e.getBlock();
        float hardness = bl.getType().getHardness();
        if (hardness <= 0) return;
        S s = states.computeIfAbsent(p.getUniqueId(), k -> new S());
        long now = System.currentTimeMillis();
        boolean same = s.start > 0 && bl.getWorld().getUID().equals(s.world)
                && bl.getX() == s.x && bl.getY() == s.y && bl.getZ() == s.z;
        long start = s.start;
        s.start = 0;
        if (!same || now - start > 300_000L) {
            if (b("detect-no-start", false)) {
                flag(p, 1.0, "kazma başlatılmadan blok kırıldı: " + bl.getType());
            }
            return;
        }
        ItemStack tool = p.getInventory().getItemInMainHand();
        double speed = toolSpeed(tool.getType());
        int eff = ServerUtil.enchantLevel(tool, "efficiency");
        int haste = ServerUtil.effectAmplifier(p, "FAST_DIGGING", "HASTE");
        double minTicks = MoveMath.minBreakTicks(hardness, speed, eff, haste);
        if (minTicks < d("min-ticks", 3)) return;
        double elapsed = (now - start) / 50.0;
        double allowed = minTicks * d("tolerance", 0.7);
        if (elapsed < allowed) {
            s.buf += 1;
            if (s.buf >= d("buffer", 2)) {
                flag(p, 1.0 + 3.0 * (allowed - elapsed) / allowed,
                        String.format(Locale.ROOT, "%s: %.1f tick (alt sınır %.1f)", bl.getType(), elapsed, allowed));
                s.buf = 1;
            }
        } else {
            s.buf = Math.max(0, s.buf - 0.5);
        }
    }

    @Override
    public void onQuit(UUID id) {
        states.remove(id);
    }
}
