package com.voidguard.anticheat.checks;

import com.voidguard.anticheat.VoidGuard;
import com.voidguard.anticheat.data.MoveCtx;
import com.voidguard.anticheat.data.PlayerData;
import com.voidguard.anticheat.math.RateWindow;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

/**
 * Inventory: (1) saniyedeki aşırı envanter tıklaması (ChestStealer/AutoArmor tarzı), (2) sunucunun bildiği
 * bir arayüz açıkken sürdürülen yürüme (InventoryMove), (3) yaratıcı moda geçmeden yaratıcı tıklaması, (4) ölüyken tıklama.
 */
public final class InventoryCheck extends Check {
    private static final class S {
        final RateWindow clicks = new RateWindow(1000, 128);
        long openTime;
        double buf;
    }

    private final Map<UUID, S> states = new HashMap<>();

    public InventoryCheck(VoidGuard plugin) {
        super(plugin, "Inventory", "inventory");
    }

    private S st(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new S());
    }

    @Override
    public void onInventoryOpen(Player p, PlayerData d, InventoryOpenEvent e) {
        st(p).openTime = System.currentTimeMillis();
    }

    @Override
    public void onInventoryClose(Player p, PlayerData d, InventoryCloseEvent e) {
        S s = st(p);
        s.openTime = 0;
        s.buf = 0;
    }

    @Override
    public void onInventoryClick(Player p, PlayerData d, InventoryClickEvent e) {
        S s = st(p);
        int n = s.clicks.hit(System.currentTimeMillis());
        if (n > i("max-clicks-per-second", 30)) {
            flag(p, 1.0, "saniyede " + n + " envanter tıklaması");
        }
        if (e.getClick() == ClickType.CREATIVE && p.getGameMode() != GameMode.CREATIVE) {
            flag(p, 3.0, "yaratıcı mod tıklaması (oyuncu yaratıcı modda değil)");
        }
        if (p.isDead()) {
            flag(p, 1.5, "ölüyken envanter tıklaması");
        }
    }

    @Override
    public void onMove(MoveCtx c) {
        S s = states.get(c.player.getUniqueId());
        if (s == null || s.openTime == 0) return;
        if (c.exempt() || c.inLiquid() || (!c.nearGround() && c.dy != 0)) {
            s.buf = 0;
            return;
        }
        if (c.now - s.openTime < i("move-grace-ms", 800)) return;
        if (c.dh > d("move-speed", 0.12) && !c.onIce()) {
            s.buf += 1;
            if (s.buf >= d("buffer", 4)) {
                flag(c.player, 1.5, String.format(Locale.ROOT, "arayüz açıkken yürüme: %.3f blok/tick", c.dh));
                s.buf = 2;
            }
        } else {
            s.buf = Math.max(0, s.buf - 1);
        }
    }

    @Override
    public void onQuit(UUID id) {
        states.remove(id);
    }
}
