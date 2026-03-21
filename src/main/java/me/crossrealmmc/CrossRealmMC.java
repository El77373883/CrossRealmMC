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

        // Verificar configuracion antes de arrancar
        if (!checkConfig()) return;

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        getCommand("crmc").setExecutor(new CRMCCommand(this));
        getCommand("crmc").setTabCompleter(new CRMCCommand(this));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CRMCPlaceholder(this).register();
            log("&aPlaceholderAPI &7detectado y registrado.");
        }

        if (Bukkit.getPluginManager().getPlugin("ViaVersion") != null) {
            log("&aViaVersion &7detectado.");
        }

        if (Bukkit.getPluginManager().getPlugin("ViaBackwards") != null) {
            log("&aViaBackwards &7detectado.");
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

    // ─────────────────────────────────────────
    // VERIFICACION DE CONFIG
    // ─────────────────────────────────────────
    private boolean checkConfig() {
        boolean valid = true;

        String javaIp = configManager.getJavaIp();
        int javaPort  = configManager.getJavaPort();
        int bedrockPort = configManager.getBedrockPort();

        if (javaIp == null || javaIp.equals("TU_IP_AQUI") || javaIp.isEmpty()) {
            printConfigError(
                "§c  ✘  No has configurado la IP de tu servidor Java!",
                "§7  Ve a §eplugins/CrossRealmMC/config.yml",
                "§7  Busca: §eserver.java-ip",
                "§7  Ponle la IP de tu servidor, ejemplo: §a192.168.1.1",
                "§7  o si es local: §a127.0.0.1"
            );
            valid = false;
        }

        if (javaPort <= 0 || javaPort > 65535) {
            printConfigError(
                "§c  ✘  El puerto Java es invalido!",
                "§7  Ve a §eplugins/CrossRealmMC/config.yml",
                "§7  Busca: §eserver.java-port",
                "§7  Ponle el puerto de tu servidor Java, ejemplo: §a25565"
            );
            valid = false;
        }

        if (bedrockPort <= 0 || bedrockPort > 65535) {
            printConfigError(
                "§c  ✘  El puerto Bedrock es invalido!",
                "§7  Ve a §eplugins/CrossRealmMC/config.yml",
                "§7  Busca: §eserver.bedrock-port",
                "§7  Puerto recomendado: §a19132"
            );
            valid = false;
        }

        if (!valid) {
            c("§8╔══════════════════════════════════════════════════════════════╗");
            c("§8║  §c⚠  CrossRealmMC DETENIDO — Corrige la config y reinicia    §8║");
            c("§8╚══════════════════════════════════════════════════════════════╝");
        }

        return valid;
    }

    private void printConfigError(String... lines) {
        c("");
        c("§8╔══════════════════════════════════════════════════════════════╗");
        c("§8║  §e⚠  CrossRealmMC — Error de Configuracion                  §8║");
        c("§8╠══════════════════════════════════════════════════════════════╣");
        for (String line : lines) {
            c("§8║  " + line);
        }
        c("§8╠══════════════════════════════════════════════════════════════╣");
        c("§8║  §7Hecho por §bsoyadrianyt001                                  §8║");
        c("§8╚══════════════════════════════════════════════════════════════╝");
        c("");
    }

    // ─────────────────────────────────────────
    // BANNER ULTRA PREMIUM
    // ─────────────────────────────────────────
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
        for (String line : lines) c(line);
    }

    private void printStartupDone() {
        c("§8[§b✦ CrossRealmMC§8] §7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        c("§8[§b✦ CrossRealmMC§8] §a ✔  Plugin cargado y listo.");
        c("§8[§b✦ CrossRealmMC§8] §7    Puerto Bedrock §8: §e" + configManager.getBedrockPort());
        c("§8[§b✦ CrossRealmMC§8] §7    Puerto Java    §8: §a" + configManager.getJavaPort());
        c("§8[§b✦ CrossRealmMC§8] §7    IP Java        §8: §f" + configManager.getJavaIp());
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
