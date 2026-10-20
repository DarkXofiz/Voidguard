package com.voidguard.anticheat.data;

import com.voidguard.anticheat.VoidGuard;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class PlayerDataManager {
    private final VoidGuard plugin;
    private final Map<UUID, PlayerData> map = new ConcurrentHashMap<>();

    public PlayerDataManager(VoidGuard plugin) {
        this.plugin = plugin;
    }

    public PlayerData get(Player p) {
        return map.computeIfAbsent(p.getUniqueId(),
                id -> new PlayerData(id, p.getName(), plugin.getConfig().getInt("violations.evidence-max", 6)));
    }

    public PlayerData getIfPresent(UUID id) {
        return map.get(id);
    }

    public void remove(UUID id) {
        map.remove(id);
    }

    public Collection<PlayerData> all() {
        return map.values();
    }
}
