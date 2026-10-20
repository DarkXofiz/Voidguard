package com.voidguard.anticheat;

import com.voidguard.anticheat.checks.CheckManager;
import com.voidguard.anticheat.commands.VgCommand;
import com.voidguard.anticheat.data.PlayerDataManager;
import com.voidguard.anticheat.effects.PunishmentEffect;
import com.voidguard.anticheat.listeners.ActionListener;
import com.voidguard.anticheat.listeners.BlockListener;
import com.voidguard.anticheat.listeners.CombatListener;
import com.voidguard.anticheat.listeners.ConnectionListener;
import com.voidguard.anticheat.listeners.InventoryListener;
import com.voidguard.anticheat.listeners.MovementListener;
import com.voidguard.anticheat.punishments.PunishmentManager;
import com.voidguard.anticheat.punishments.ViolationManager;
import com.voidguard.anticheat.storage.Database;
import com.voidguard.anticheat.util.Exemptions;
import com.voidguard.anticheat.util.PerformanceGate;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** VOIDGUARD AntiCheat ana sınıfı. Hedef: Paper/Purpur 1.20.4+ (derleme: paper-api 1.20.4). */
public final class VoidGuard extends JavaPlugin {
    private Database database;
    private PlayerDataManager dataManager;
    private Exemptions exemptions;
    private PerformanceGate gate;
    private PunishmentEffect effect;
    private ViolationManager violations;
    private PunishmentManager punishments;
    private CheckManager checks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        database = new Database(this);
        try {
            database.init();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "SQLite başlatılamadı. Sunucunuzun org.sqlite.JDBC sürücüsünü içerdiğinden emin olun. Eklenti kapatılıyor.", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        dataManager = new PlayerDataManager(this);
        exemptions = new Exemptions(this);
        gate = new PerformanceGate(this);
        effect = new PunishmentEffect(this);
        violations = new ViolationManager(this);
        punishments = new PunishmentManager(this);
        checks = new CheckManager(this);

        reloadAll();
        punishments.load();

        org.bukkit.plugin.PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new MovementListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new BlockListener(this), this);
        pm.registerEvents(new InventoryListener(this), this);
        pm.registerEvents(new ActionListener(this), this);

        PluginCommand cmd = getCommand("voidguard");
        if (cmd != null) {
            VgCommand vg = new VgCommand(this);
            cmd.setExecutor(vg);
            cmd.setTabCompleter(vg);
        }

        gate.start();
        for (Player p : Bukkit.getOnlinePlayers()) dataManager.get(p); // /reload sonrası mevcut oyuncular
        getLogger().info("VOIDGUARD " + getDescription().getVersion() + " etkin. Kontroller: " + checks.all().size());
    }

    @Override
    public void onDisable() {
        if (gate != null) gate.stop();
        if (effect != null) effect.shutdown(); // bekleyen cezalar kaydedilir
        if (database != null) database.close();
    }

    public void reloadAll() {
        reloadConfig();
        exemptions.reload();
        gate.reload();
        violations.reload();
        checks.reload();
    }

    public Database db() { return database; }
    public PlayerDataManager data() { return dataManager; }
    public Exemptions exemptions() { return exemptions; }
    public PerformanceGate gate() { return gate; }
    public PunishmentEffect effect() { return effect; }
    public ViolationManager violations() { return violations; }
    public PunishmentManager punishments() { return punishments; }
    public CheckManager checks() { return checks; }

    // --- mesaj yardımcıları ---
    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String replace(String text, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) text = text.replace(kv[i], kv[i + 1]);
        return text;
    }

    /** Önek olmadan mesaj. */
    public String msgRaw(String key, String... kv) {
        return color(replace(getConfig().getString("messages." + key, "&cEksik mesaj: " + key), kv));
    }

    /** Önekli mesaj. */
    public String msg(String key, String... kv) {
        return color(getConfig().getString("messages.prefix", "&8[&5VOID&cGUARD&8] ")) + msgRaw(key, kv);
    }

    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(msg(key, kv));
    }
}
