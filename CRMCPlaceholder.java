package me.crossrealmmc.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.crossrealmmc.CrossRealmMC;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CRMCPlaceholder extends PlaceholderExpansion {

    private final CrossRealmMC plugin;

    public CRMCPlaceholder(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "crossrealmmc"; }
    @Override public @NotNull String getAuthor() { return "soyadrianyt001"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";
        switch (identifier.toLowerCase()) {
            case "edition":
                return plugin.getPlayerDetector().isBedrockPlayer(player) ? "BEDROCK" : "JAVA";
            case "is_bedrock":
                return String.valueOf(plugin.getPlayerDetector().isBedrockPlayer(player));
            case "is_java":
                return String.valueOf(plugin.getPlayerDetector().isJavaPlayer(player));
            case "bedrock_version":
                return plugin.getPlayerDetector().isBedrockPlayer(player)
                        ? plugin.getPlayerDetector().getBedrockVersion(player.getUniqueId()) : "N/A";
            case "online_bedrock":
                return String.valueOf(plugin.getPlayerDetector().getOnlineBedrockCount());
            case "online_java":
                return String.valueOf(plugin.getPlayerDetector().getOnlineJavaCount());
            default:
                return null;
        }
    }
}
