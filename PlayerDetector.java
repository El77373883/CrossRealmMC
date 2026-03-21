package me.crossrealmmc.detection;

import me.crossrealmmc.CrossRealmMC;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDetector {

    private final CrossRealmMC plugin;

    // UUID -> tipo de jugador
    private final Map<UUID, PlayerType> playerTypes = new HashMap<>();
    // UUID -> version de Bedrock
    private final Map<UUID, String> bedrockVersions = new HashMap<>();
    // IP -> timestamp ultimo intento de conexion (cooldown)
    private final Map<String, Long> reconnectCooldowns = new HashMap<>();

    public enum PlayerType {
        JAVA, BEDROCK
    }

    public PlayerDetector(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    public void registerPlayer(UUID uuid, PlayerType type, String bedrockVersion) {
        playerTypes.put(uuid, type);
        if (type == PlayerType.BEDROCK && bedrockVersion != null) {
            bedrockVersions.put(uuid, bedrockVersion);
        }
        plugin.debugLog("Registrado jugador " + uuid + " como " + type +
                (bedrockVersion != null ? " v" + bedrockVersion : ""));
    }

    public void unregisterPlayer(UUID uuid) {
        playerTypes.remove(uuid);
        bedrockVersions.remove(uuid);
    }

    public boolean isBedrockPlayer(Player player) {
        return isBedrockPlayer(player.getUniqueId());
    }

    public boolean isBedrockPlayer(UUID uuid) {
        return playerTypes.getOrDefault(uuid, PlayerType.JAVA) == PlayerType.BEDROCK;
    }

    public boolean isJavaPlayer(Player player) {
        return !isBedrockPlayer(player);
    }

    public PlayerType getPlayerType(UUID uuid) {
        return playerTypes.getOrDefault(uuid, PlayerType.JAVA);
    }

    public String getBedrockVersion(UUID uuid) {
        return bedrockVersions.getOrDefault(uuid, "Unknown");
    }

    public String getFormattedName(String name, PlayerType type) {
        if (type == PlayerType.BEDROCK) {
            return plugin.getConfigManager().getBedrockPrefix() + name;
        }
        return name;
    }

    public boolean isBedrockUsername(String name) {
        return name.startsWith(plugin.getConfigManager().getBedrockPrefix());
    }

    // Cooldown de reconexion
    public boolean isOnCooldown(String ip) {
        if (!plugin.getConfigManager().isReconnectCooldownEnabled()) return false;
        Long lastAttempt = reconnectCooldowns.get(ip);
        if (lastAttempt == null) return false;
        long seconds = plugin.getConfigManager().getReconnectCooldownSeconds();
        return (System.currentTimeMillis() - lastAttempt) < (seconds * 1000L);
    }

    public long getRemainingCooldown(String ip) {
        Long lastAttempt = reconnectCooldowns.get(ip);
        if (lastAttempt == null) return 0;
        long seconds = plugin.getConfigManager().getReconnectCooldownSeconds();
        long remaining = (seconds * 1000L) - (System.currentTimeMillis() - lastAttempt);
        return Math.max(0, remaining / 1000);
    }

    public void setCooldown(String ip) {
        reconnectCooldowns.put(ip, System.currentTimeMillis());
    }

    public int getOnlineBedrockCount() {
        return (int) playerTypes.values().stream()
                .filter(t -> t == PlayerType.BEDROCK)
                .count();
    }

    public int getOnlineJavaCount() {
        return (int) playerTypes.values().stream()
                .filter(t -> t == PlayerType.JAVA)
                .count();
    }
}
