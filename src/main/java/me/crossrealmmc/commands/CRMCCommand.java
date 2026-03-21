package me.crossrealmmc.commands;

import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.detection.PlayerDetector;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class CRMCCommand implements CommandExecutor, TabCompleter {

    private final CrossRealmMC plugin;
    private boolean maintenanceBedrock = false;
    private boolean maintenanceJava = false;

    public CRMCCommand(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("crossrealmmc.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.getConfigManager().getMessage("cmd-help"));
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "help":
                sender.sendMessage(plugin.getConfigManager().getMessage("cmd-help"));
                break;

            case "reload":
                plugin.getConfigManager().reload();
                sender.sendMessage(plugin.getConfigManager().getMessage("cmd-reload"));
                break;

            case "info":
                PlayerDetector det = plugin.getPlayerDetector();
                String bridgeStatus = plugin.getRakNetServer().isRunning() ? "§aON" : "§cOFF";
                sender.sendMessage(plugin.getConfigManager().getMessage("cmd-info",
                        "{version}", plugin.getDescription().getVersion(),
                        "{java-players}", String.valueOf(det.getOnlineJavaCount()),
                        "{bedrock-players}", String.valueOf(det.getOnlineBedrockCount()),
                        "{bridge-status}", bridgeStatus,
                        "{bedrock-port}", String.valueOf(plugin.getConfigManager().getBedrockPort()),
                        "{java-online}", String.valueOf(plugin.getConfigManager().isJavaOnlineMode()),
                        "{bedrock-online}", String.valueOf(plugin.getConfigManager().isBedrockOnlineMode())
                ));
                break;

            case "list":
                PlayerDetector det2 = plugin.getPlayerDetector();
                StringBuilder javaList = new StringBuilder();
                StringBuilder bedrockList = new StringBuilder();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (plugin.getPlayerDetector().isBedrockPlayer(p)) {
                        bedrockList.append(p.getName()).append(", ");
                    } else {
                        javaList.append(p.getName()).append(", ");
                    }
                }
                String jl = javaList.length() > 0 ? javaList.substring(0, javaList.length()-2) : "Ninguno";
                String bl = bedrockList.length() > 0 ? bedrockList.substring(0, bedrockList.length()-2) : "Ninguno";
                sender.sendMessage(plugin.getConfigManager().getMessage("cmd-list",
                        "{java-count}", String.valueOf(det2.getOnlineJavaCount()),
                        "{bedrock-count}", String.valueOf(det2.getOnlineBedrockCount()),
                        "{java-list}", jl,
                        "{bedrock-list}", bl
                ));
                break;

            case "stats":
                sender.sendMessage(plugin.getConfigManager().getMessage("cmd-stats",
                        "{total-bedrock}", String.valueOf(plugin.getConnectionLogger().getTotalBedrock()),
                        "{total-java}", String.valueOf(plugin.getConnectionLogger().getTotalJava()),
                        "{anticheat-kicks}", String.valueOf(plugin.getConnectionLogger().getAntiCheatKicks()),
                        "{active-bans}", String.valueOf(plugin.getBanManager().getBanCount())
                ));
                break;

            case "ban":
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /crmc ban <jugador> [java|bedrock|all]");
                    return true;
                }
                String banTarget = args[1];
                String edition = args.length >= 3 ? args[2].toUpperCase() : "ALL";
                if (!edition.equals("JAVA") && !edition.equals("BEDROCK") && !edition.equals("ALL")) {
                    edition = "ALL";
                }
                if (plugin.getBanManager().ban(banTarget, edition, "Baneado por admin")) {
                    plugin.getConnectionLogger().logBan(banTarget, edition, sender.getName());
                    sender.sendMessage(plugin.getConfigManager().getMessage("ban-success",
                            "{player}", banTarget, "{edition}", edition));
                    Player banPlayer = Bukkit.getPlayer(banTarget);
                    if (banPlayer != null) banPlayer.kickPlayer(plugin.getConfigManager().getMessage("banned-message"));
                } else {
                    sender.sendMessage(plugin.getConfigManager().getMessage("ban-already", "{player}", banTarget));
                }
                break;

            case "unban":
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /crmc unban <jugador>");
                    return true;
                }
                if (plugin.getBanManager().unban(args[1])) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("unban-success", "{player}", args[1]));
                } else {
                    sender.sendMessage(plugin.getConfigManager().getMessage("ban-not-found", "{player}", args[1]));
                }
                break;

            case "maintenance":
                if (args.length < 3) {
                    sender.sendMessage("§cUso: /crmc maintenance <bedrock|java> <on|off>");
                    return true;
                }
                boolean state = args[2].equalsIgnoreCase("on");
                if (args[1].equalsIgnoreCase("bedrock")) {
                    maintenanceBedrock = state;
                    sender.sendMessage("§7Mantenimiento Bedrock: " + (state ? "§cON" : "§aOFF"));
                } else if (args[1].equalsIgnoreCase("java")) {
                    maintenanceJava = state;
                    sender.sendMessage("§7Mantenimiento Java: " + (state ? "§cON" : "§aOFF"));
                }
                break;

            default:
                sender.sendMessage(plugin.getConfigManager().getMessage("unknown-command"));
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1)
            return Arrays.asList("help", "info", "list", "reload", "ban", "unban", "stats", "maintenance");
        if (args.length == 2 && args[0].equalsIgnoreCase("ban"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("ban"))
            return Arrays.asList("java", "bedrock", "all");
        if (args.length == 2 && args[0].equalsIgnoreCase("maintenance"))
            return Arrays.asList("bedrock", "java");
        if (args.length == 3 && args[0].equalsIgnoreCase("maintenance"))
            return Arrays.asList("on", "off");
        return List.of();
    }

    public boolean isMaintenanceBedrock() { return maintenanceBedrock; }
    public boolean isMaintenanceJava() { return maintenanceJava; }
}
