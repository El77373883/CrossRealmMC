package me.crossrealmmc.config;

import me.crossrealmmc.CrossRealmMC;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public class ConfigManager {

    private final CrossRealmMC plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private String language;

    public ConfigManager(CrossRealmMC plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void loadAll() {
        // config.yml
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.language = config.getString("language", "es");

        // messages.yml
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) plugin.saveResource("messages.yml", false);
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Crear carpeta de logs
        File logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists()) logsFolder.mkdirs();

        // Crear bans.yml si no existe
        File bansFile = new File(plugin.getDataFolder(), "bans.yml");
        if (!bansFile.exists()) {
            try { bansFile.createNewFile(); } catch (Exception ignored) {}
        }
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.language = config.getString("language", "es");
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String getMessage(String key) {
        String msg = messages.getString(language + "." + key, "&cMensaje no encontrado: " + key);
        return msg.replace("&", "§");
    }

    public String getMessage(String key, String... replacements) {
        String msg = getMessage(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    // Server
    public String getJavaIp() { return config.getString("server.java-ip", "127.0.0.1"); }
    public int getJavaPort() { return config.getInt("server.java-port", 25565); }
    public String getBedrockIp() { return config.getString("server.bedrock-ip", "0.0.0.0"); }
    public int getBedrockPort() { return config.getInt("server.bedrock-port", 19132); }
    public String getMotdLine1() { return config.getString("server.motd-line1", "CrossRealmMC"); }
    public String getMotdLine2() { return config.getString("server.motd-line2", "Java + Bedrock"); }
    public int getMaxBedrockPlayers() { return config.getInt("server.max-bedrock-players", 0); }

    // Auth
    public boolean isJavaOnlineMode() { return config.getBoolean("authentication.java-online-mode", true); }
    public boolean isBedrockOnlineMode() { return config.getBoolean("authentication.bedrock-online-mode", false); }
    public String getBedrockPrefix() { return config.getString("authentication.bedrock-prefix", "."); }

    // Versions
    public List<String> getSupportedBedrockVersions() {
        return config.getStringList("bedrock-versions");
    }

    // Whitelist
    public boolean isBedrockWhitelistEnabled() { return config.getBoolean("whitelist.bedrock-whitelist", false); }
    public boolean isJavaWhitelistEnabled() { return config.getBoolean("whitelist.java-whitelist", false); }

    // Anti-cheat
    public boolean isAntiCheatEnabled() { return config.getBoolean("anticheat.enabled", true); }
    public boolean isCheckMovement() { return config.getBoolean("anticheat.check-movement", true); }
    public boolean isCheckSpeed() { return config.getBoolean("anticheat.check-speed", true); }
    public String getAntiCheatAction() { return config.getString("anticheat.action", "KICK"); }

    // Cooldown
    public boolean isReconnectCooldownEnabled() { return config.getBoolean("reconnect-cooldown.enabled", true); }
    public int getReconnectCooldownSeconds() { return config.getInt("reconnect-cooldown.seconds", 5); }

    // Logging
    public boolean isLoggingEnabled() { return config.getBoolean("logging.enabled", true); }
    public String getLogFile() { return config.getString("logging.file", "plugins/CrossRealmMC/logs/connections.txt"); }
    public boolean isLogBedrockJoin() { return config.getBoolean("logging.log-bedrock-join", true); }
    public boolean isLogJavaJoin() { return config.getBoolean("logging.log-java-join", false); }
    public boolean isLogDisconnect() { return config.getBoolean("logging.log-disconnect", true); }

    // Debug
    public boolean isDebug() { return config.getBoolean("debug", false); }

    // Auto-restart
    public boolean isAutoRestartEnabled() { return config.getBoolean("auto-restart.enabled", true); }
    public int getAutoRestartDelay() { return config.getInt("auto-restart.delay-seconds", 10); }

    // Chat
    public boolean isShowEditionPrefix() { return config.getBoolean("chat.show-edition-prefix", true); }
    public String getJavaChatPrefix() { return config.getString("chat.java-prefix", "&7[&aJava&7]").replace("&", "§"); }
    public String getBedrockChatPrefix() { return config.getString("chat.bedrock-prefix", "&7[&bBedrock&7]").replace("&", "§"); }

    public String getLanguage() { return language; }
}
