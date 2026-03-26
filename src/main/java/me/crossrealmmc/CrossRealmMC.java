package me.crossrealmmc;

import me.crossrealmmc.anticheat.AntiCheat;
import me.crossrealmmc.ban.BanManager;
import me.crossrealmmc.bridge.JavaPacketInterceptor;
import me.crossrealmmc.commands.CRMCCommand;
import me.crossrealmmc.config.ConfigManager;
import me.crossrealmmc.detection.PlayerDetector;
import me.crossrealmmc.listeners.PlayerListener;
import me.crossrealmmc.log.ConnectionLogger;
import me.crossrealmmc.placeholder.CRMCPlaceholder;
import me.crossrealmmc.raknet.RakNetServer;
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
    private JavaPacketInterceptor javaPacketInterceptor;
    private boolean floodgateEnabled = false;

    @Override
    public void onEnable() {
        instance = this;
        printBanner();
        this.configManager = new ConfigManager(this);
        this.connectionLogger = new ConnectionLogger(this);
        this.playerDetector = new PlayerDetector(this);
        this.banManager = new BanManager(this);
        this.antiCheat = new AntiCheat(this);

        if (!checkConfig()) return;

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        getCommand("crmc").setExecutor(new CRMCCommand(this));
        getCommand("crmc").setTabCompleter(new CRMCCommand(this));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
            new CRMCPlaceholder(this).register();

        if (Bukkit.getPluginManager().getPlugin("ViaVersion") != null)
            log("&aViaVersion &7detectado.");
        if (Bukkit.getPluginManager().getPlugin("ViaBackwards") != null)
            log("&aViaBackwards &7detectado.");

        // Detectar Floodgate
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            floodgateEnabled = true;
            log("&aFloodgate detectado - Autenticación delegada");
        } else {
            log("&eFloodgate no detectado - Usando autenticación propia");
        }

        this.rakNetServer = new RakNetServer(this);
        rakNetServer.start();

        this.javaPacketInterceptor = new JavaPacketInterceptor(this, rakNetServer.getHandler());
        javaPacketInterceptor.start();

        printStartupDone();
    }

    @Override
    public void onDisable() {
        if (rakNetServer != null) rakNetServer.stop();
        if (connectionLogger != null) connectionLogger.close();
        printShutdown();
    }

    private boolean checkConfig() {
        boolean valid = true;
        int bedrockPort = configManager.getBedrockPort();
        if (bedrockPort <= 0 || bedrockPort > 65535) {
            printConfigError(
                "§c  ✘  El puerto Bedrock es invalido!",
                "§7  Ve a: §eplugins/CrossRealmMC/config.yml",
                "§7  Busca: §ebedrock.port",
                "§7  Puerto recomendado: §a25565"
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
        for (String line : lines) c("§8║  " + line);
        c("§8╠══════════════════════════════════════════════════════════════╣");
        c("§8║  §7Hecho por §bsoyadrianyt001                                  §8║");
        c("§8╚══════════════════════════════════════════════════════════════╝");
        c("");
    }

    private void printBanner() {
        c("");
        c("§8╔══════════════════════════════════════════════════════════════╗");
        c("§8║  §b██████╗██████╗  ██████╗ ███████╗███████╗                 §8║");
        c("§8║  §b██╔════╝██╔══██╗██╔═══██╗██╔════╝██╔════╝                §8║");
        c("§8║  §b██║     ██████╔╝██║   ██║███████╗███████╗                §8║");
        c("§8║  §b██║     ██╔══██╗██║   ██║╚════██║╚════██║                §8║");
        c("§8║  §b╚██████╗██║  ██║╚██████╔╝███████║███████║                §8║");
        c("§8║  §b ╚═════╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚══════╝               §8║");
        c("§8║                                                              §8║");
        c("§8║  §3██████╗ ███████╗ █████╗ ██╗     ███╗   ███╗ ██████╗      §8║");
        c("§8║  §3██╔══██╗██╔════╝██╔══██╗██║     ████╗ ████║██╔════╝      §8║");
        c("§8║  §3██████╔╝█████╗  ███████║██║     ██╔████╔██║██║           §8║");
        c("§8║  §3██╔══██╗██╔══╝  ██╔══██║██║     ██║╚██╔╝██║██║           §8║");
        c("§8║  §3██║  ██║███████╗██║  ██║███████╗██║ ╚═╝ ██║╚██████╗      §8║");
        c("§8║  §3╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝╚══════╝╚═╝     ╚═╝ ╚═════╝     §8║");
        c("§8║                                                              §8║");
        c("§8║  §7Version §a1.0.0  §8•  §7Autor §bsoyadrianyt001               §8║");
        c("§8║  §7Bridge Bedrock§8↔§7Java  §8•  §7Sin Geyser  §8•  §7Sin Floodgate §8║");
        c("§8║  §7Bedrock: §e26.0 §8│ §e26.1 §8│ §e26.2 §8│ §e26.3                 §8║");
        c("§8╚══════════════════════════════════════════════════════════════╝");
        c("");
    }

    private void printStartupDone() {
        c("§8[§b✦ CrossRealmMC§8] §7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        c("§8[§b✦ CrossRealmMC§8] §a ✔  Plugin cargado y listo.");
        c("§8[§b✦ CrossRealmMC§8] §7    Puerto Bedrock §8: §e" + configManager.getBedrockPort());
        if (floodgateEnabled) {
            c("§8[§b✦ CrossRealmMC§8] §7    Floodgate     §8: §a✓ Detectado");
        }
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

    public boolean isFloodgateEnabled() {
        return floodgateEnabled;
    }

    public static CrossRealmMC getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ConnectionLogger getConnectionLogger() {
        return connectionLogger;
    }

    public PlayerDetector getPlayerDetector() {
        return playerDetector;
    }

    public BanManager getBanManager() {
        return banManager;
    }

    public AntiCheat getAntiCheat() {
        return antiCheat;
    }

    public RakNetServer getRakNetServer() {
        return rakNetServer;
    }

    public JavaPacketInterceptor getJavaPacketInterceptor() {
        return javaPacketInterceptor;
    }
}
