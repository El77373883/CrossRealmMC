package me.crossrealmmc;

import me.crossrealmmc.anticheat.AntiCheat;
import me.crossrealmmc.ban.BanManager;
import me.crossrealmmc.commands.CRMCCommand;
import me.crossrealmmc.config.ConfigManager;
import me.crossrealmmc.detection.PlayerDetector;
import me.crossrealmmc.listeners.PlayerListener;
import me.crossrealmmc.log.ConnectionLogger;
import me.crossrealmmc.placeholder.CRMCPlaceholder;
import me.crossrealmmc.raknet.RakNetServer;
import me.crossrealmmc.realmgate.RealmGate;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class CrossRealmMC extends JavaPlugin {

    private static CrossRealmMC instance;

    private ConfigManager configManager;
    private ConnectionLogger connectionLogger;
    private PlayerDetector playerDetector;
    private BanManager banManager;
    private AntiCheat antiCheat;
    private RakNetServer rakNetServer;
    private RealmGate realmGate;

    @Override
    public void onEnable() {
        instance = this;

        printBanner();

        this.configManager    = new ConfigManager(this);
        this.connectionLogger = new ConnectionLogger(this);
        this.playerDetector   = new PlayerDetector(this);
        this.banManager       = new BanManager(this);
        this.antiCheat        = new AntiCheat(this);
        this.realmGate        = new RealmGate(this);

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        getCommand("crmc").setExecutor(new CRMCCommand(this));
        getCommand("crmc").setTabCompleter(new CRMCCommand(this));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CRMCPlaceholder(this).register();
            log("&aPlaceholderAPI &7detectado y registrado.");
        }

        this.rakNetServer = new RakNetServer(this);
        rakNetServer.start();

        printStartupDone();
    }

    @Override
    public void onDisable() {
        if (rakNetServer != null)     rakNetServer.stop();
        if (connectionLogger != null) connectionLogger.close();
        printShutdown();
    }

    private void printBanner() {
        String[] lines = {
            "",
            "§8╔══════════════════════════════════════════════════════════════╗",
            "§8║  §b██████╗██████╗  ██████╗ ███████╗███████╗                 §8║",
            "§8║  §b██╔════╝██╔══██╗██╔═══██╗██╔════╝██╔════╝                §8║",
            "§8║  §b██║     ██████╔╝██║   ██║███████╗███████╗                §8║",
            "§8║  §b██║     ██╔══██╗██║   ██║╚════██║╚════██║                §8║",
            "§8║  §b╚██████╗██║  ██║╚██████╔╝███████║███████║                §8║",
            "§8║  §b ╚═════╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚══════╝               §8║",
            "§8║                                                              §8║",
            "§8║  §3██████╗ ███████╗ █████╗ ██╗     ███╗   ███╗ ██████╗      §8║",
            "§8║  §3██╔══██╗██╔════╝██╔══██╗██║     ████╗ ████║██╔════╝      §8║",
            "§8║  §3██████╔╝█████╗  ███████║██║     ██╔████╔██║██║           §8║",
            "§8║  §3██╔══██╗██╔══╝  ██╔══██║██║     ██║╚██╔╝██║██║           §8║",
            "§8║  §3██║  ██║███████╗██║  ██║███████╗██║ ╚═╝ ██║╚██████╗      §8║",
            "§8║  §3╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝╚══════╝╚═╝     ╚═╝ ╚═════╝     §8║",
            "§8║                                                              §8║",
            "§8║  §7Version §a1.0.0  §8•  §7Autor §bsoyadrianyt001               §8║",
            "§8║  §7Bridge Bedrock§8↔§7Java  §8•  §7Sin Geyser  §8•  §7Sin Floodgate §8║",
            "§8║  §7Bedrock: §e26.0 §8│ §e26.1 §8│ §e26.2 §8│ §e26.3                 §8║",
            "§8╚══════════════════════════════════════════════════════════════╝",
            ""
        };
        for (String line : lines) Bukkit.getConsoleSender().sendMessage(line);
    }

    private void printStartupDone() {
        c("§8[§b✦ CrossRealmMC§8] §7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        c("§8[§b✦ CrossRealmMC§8] §a ✔  Plugin cargado y listo.");
        c("§8[§b✦ CrossRealmMC§8] §7    Puerto Bedrock §8: §e" + configManager.getBedrockPort());
        c("§8[§b✦ CrossRealmMC§8] §7    Puerto Java    §8: §a" + configManager.getJavaPort());
        c("§8[§b✦ CrossRealmMC§8] §7    Online Java    §8: §f" + configManager.isJavaOnlineMode());
        c("§8[§b✦ CrossRealmMC§8] §7    Online Bedrock §8: §f" + configManager.isBedrockOnlineMode());
        c("§8[§b✦ CrossRealmMC§8] §7    Idioma         §8: §f" + configManager.getLanguage().toUpperCase());
        c("§8[§b✦ CrossRealmMC§8] §7    Hecho por      §8: §bsoyadrianyt001");
        c("§8[§b✦ CrossRealmMC§8] §7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void printShutdown() {
        c("§8[§b✦ CrossRealmMC§8] §7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        c("§8[§b✦ CrossRealmMC§8] §c ✘  Plugin detenido correctamente.");
        c("§8[§b✦ CrossRealmMC§8] §7    Hecho con §c❤ §7por §bsoyadrianyt001");
        c("§8[§b✦ CrossRealmMC§8] §7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void c(String msg) {
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    public void log(String message) {
        c("§8[§b✦ CrossRealmMC§8] §r" + message.replace("&", "§"));
    }

    public void debugLog(String message) {
        if (configManager != null && configManager.isDebug()) {
            c("§8[§eCrossRealmMC §7DEBUG§8] §7" + message);
        }
    }

    public static CrossRealmMC getInstance()      { return instance; }
    public ConfigManager getConfigManager()        { return configManager; }
    public ConnectionLogger getConnectionLogger()  { return connectionLogger; }
    public PlayerDetector getPlayerDetector()      { return playerDetector; }
    public BanManager getBanManager()              { return banManager; }
    public AntiCheat getAntiCheat()                { return antiCheat; }
    public RakNetServer getRakNetServer()          { return rakNetServer; }
    public RealmGate getRealmGate()                { return realmGate; }
}
