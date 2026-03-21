package me.crossrealmmc.listeners;

import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.detection.PlayerDetector;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

public class PlayerListener implements Listener {

    private final CrossRealmMC plugin;

    public PlayerListener(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        PlayerDetector detector = plugin.getPlayerDetector();

        PlayerDetector.PlayerType type;
        if (detector.isBedrockUsername(name)) {
            type = PlayerDetector.PlayerType.BEDROCK;
        } else {
            type = PlayerDetector.PlayerType.JAVA;
        }

        detector.registerPlayer(player.getUniqueId(), type, null);

        if (plugin.getBanManager().isBanned(name, type)) {
            player.kickPlayer(plugin.getConfigManager().getMessage("banned-message"));
            return;
        }

        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "Unknown";
        plugin.getConnectionLogger().logJoin(name, type, ip, type == PlayerDetector.PlayerType.BEDROCK ? "Bedrock" : "Java");

        if (plugin.getConfigManager().isShowEditionPrefix()) {
            String joinMsg = type == PlayerDetector.PlayerType.BEDROCK
                    ? plugin.getConfigManager().getMessage("player-join-bedrock",
                        "{player}", name, "{version}", "Bedrock")
                    : plugin.getConfigManager().getMessage("player-join-java",
                        "{player}", name);

            event.setJoinMessage(null);
            Bukkit.broadcastMessage(joinMsg);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerDetector detector = plugin.getPlayerDetector();
        PlayerDetector.PlayerType type = detector.getPlayerType(player.getUniqueId());

        plugin.getConnectionLogger().logDisconnect(player.getName(), type, "Disconnect");
        plugin.getAntiCheat().resetViolations(player.getUniqueId());
        detector.unregisterPlayer(player.getUniqueId());

        String leaveMsg = plugin.getConfigManager().getMessage("player-leave",
                "{player}", player.getName());
        event.setQuitMessage(leaveMsg);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getPlayerDetector().isBedrockPlayer(player)) return;
        plugin.getAntiCheat().checkMovement(player, event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfigManager().isShowEditionPrefix()) return;

        PlayerDetector.PlayerType type = plugin.getPlayerDetector().getPlayerType(player.getUniqueId());
        String prefix = type == PlayerDetector.PlayerType.BEDROCK
                ? plugin.getConfigManager().getBedrockChatPrefix()
                : plugin.getConfigManager().getJavaChatPrefix();

        event.setFormat(prefix + " §f" + player.getName() + "§7: §f%2$s");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        PlayerDetector detector = plugin.getPlayerDetector();
        PlayerDetector.PlayerType type = detector.getPlayerType(player.getUniqueId());

        plugin.getConnectionLogger().logDisconnect(player.getName(), type, event.getReason());
        plugin.getAntiCheat().resetViolations(player.getUniqueId());
        detector.unregisterPlayer(player.getUniqueId());
    }
}
