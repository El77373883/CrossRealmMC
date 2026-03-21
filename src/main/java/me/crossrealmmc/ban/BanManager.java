package me.crossrealmmc.ban;

import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.detection.PlayerDetector;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class BanManager {

    private final CrossRealmMC plugin;
    private final File bansFile;
    private YamlConfiguration bansConfig;

    // nombre -> tipo ban
    private final Map<String, BanEntry> bans = new HashMap<>();

    public static class BanEntry {
        public final String player;
        public final String edition; // JAVA, BEDROCK, ALL
        public final String date;
        public final String reason;

        public BanEntry(String player, String edition, String reason) {
            this.player = player;
            this.edition = edition;
            this.reason = reason;
            this.date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    public BanManager(CrossRealmMC plugin) {
        this.plugin = plugin;
        this.bansFile = new File(plugin.getDataFolder(), "bans.yml");
        loadBans();
    }

    private void loadBans() {
        if (!bansFile.exists()) {
            try { bansFile.createNewFile(); } catch (IOException ignored) {}
        }
        bansConfig = YamlConfiguration.loadConfiguration(bansFile);

        if (bansConfig.getConfigurationSection("bans") != null) {
            for (String key : bansConfig.getConfigurationSection("bans").getKeys(false)) {
                String edition = bansConfig.getString("bans." + key + ".edition", "ALL");
                String reason = bansConfig.getString("bans." + key + ".reason", "Baneado");
                bans.put(key.toLowerCase(), new BanEntry(key, edition, reason));
            }
        }
        plugin.debugLog("Cargados " + bans.size() + " bans.");
    }

    private void saveBans() {
        bansConfig = new YamlConfiguration();
        for (Map.Entry<String, BanEntry> entry : bans.entrySet()) {
            bansConfig.set("bans." + entry.getKey() + ".edition", entry.getValue().edition);
            bansConfig.set("bans." + entry.getKey() + ".reason", entry.getValue().reason);
            bansConfig.set("bans." + entry.getKey() + ".date", entry.getValue().date);
        }
        try {
            bansConfig.save(bansFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar bans.yml: " + e.getMessage());
        }
    }

    public boolean ban(String playerName, String edition, String reason) {
        String key = playerName.toLowerCase();
        if (bans.containsKey(key)) return false;
        bans.put(key, new BanEntry(playerName, edition.toUpperCase(), reason));
        saveBans();
        plugin.debugLog("Baneado: " + playerName + " | Edicion: " + edition);
        return true;
    }

    public boolean unban(String playerName) {
        String key = playerName.toLowerCase();
        if (!bans.containsKey(key)) return false;
        bans.remove(key);
        saveBans();
        return true;
    }

    public boolean isBanned(String playerName, PlayerDetector.PlayerType type) {
        String key = playerName.toLowerCase();
        // Quitar prefijo bedrock para buscar
        String cleanName = key.startsWith(plugin.getConfigManager().getBedrockPrefix())
                ? key.substring(plugin.getConfigManager().getBedrockPrefix().length())
                : key;

        BanEntry entry = bans.get(cleanName);
        if (entry == null) entry = bans.get(key);
        if (entry == null) return false;

        if (entry.edition.equals("ALL")) return true;
        if (entry.edition.equals("BEDROCK") && type == PlayerDetector.PlayerType.BEDROCK) return true;
        if (entry.edition.equals("JAVA") && type == PlayerDetector.PlayerType.JAVA) return true;
        return false;
    }

    public BanEntry getBan(String playerName) {
        return bans.get(playerName.toLowerCase());
    }

    public int getBanCount() {
        return bans.size();
    }
}
