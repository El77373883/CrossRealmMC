package me.crossrealmmc.anticheat;

import me.crossrealmmc.CrossRealmMC;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AntiCheat {

    private final CrossRealmMC plugin;

    // UUID -> ultima posicion
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    // UUID -> timestamp ultimo movimiento
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();
    // UUID -> violaciones acumuladas
    private final Map<UUID, Integer> violations = new HashMap<>();

    private static final double MAX_SPEED = 20.0; // bloques por segundo
    private static final int MAX_VIOLATIONS = 5;

    public AntiCheat(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    public void checkMovement(Player player, Location from, Location to) {
        if (!plugin.getConfigManager().isAntiCheatEnabled()) return;
        if (!plugin.getPlayerDetector().isBedrockPlayer(player)) return;
        if (player.hasPermission("crossrealmmc.bypass")) return;

        UUID uuid = player.getUniqueId();

        // Verificar velocidad
        if (plugin.getConfigManager().isCheckSpeed()) {
            long now = System.currentTimeMillis();
            Long lastTime = lastMoveTime.get(uuid);
            Location lastLoc = lastLocations.get(uuid);

            if (lastLoc != null && lastTime != null) {
                double timeDelta = (now - lastTime) / 1000.0;
                if (timeDelta > 0 && timeDelta < 1.0) {
                    double distance = from.distance(to);
                    double speed = distance / timeDelta;

                    if (speed > MAX_SPEED && !player.isFlying() && !player.isInsideVehicle()) {
                        addViolation(player, "Velocidad anormal: " + String.format("%.2f", speed) + " b/s");
                    }
                }
            }

            lastMoveTime.put(uuid, now);
            lastLocations.put(uuid, to);
        }

        // Verificar movimiento inválido (volar sin permiso)
        if (plugin.getConfigManager().isCheckMovement()) {
            if (!player.getAllowFlight() && !player.isOnGround()) {
                double yDiff = to.getY() - from.getY();
                if (yDiff > 2.0) {
                    addViolation(player, "Vuelo no autorizado");
                }
            }
        }
    }

    private void addViolation(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        int count = violations.getOrDefault(uuid, 0) + 1;
        violations.put(uuid, count);

        plugin.debugLog("[AntiCheat] " + player.getName() + " | " + reason + " | Violaciones: " + count);

        if (count >= MAX_VIOLATIONS) {
            violations.remove(uuid);
            String action = plugin.getConfigManager().getAntiCheatAction();
            plugin.getConnectionLogger().logAntiCheatKick(player.getName(), reason);

            if (action.equalsIgnoreCase("BAN")) {
                plugin.getBanManager().ban(player.getName(), "ALL", "AntiCheat: " + reason);
                player.kickPlayer(plugin.getConfigManager().getMessage("anticheat-kick"));
            } else {
                player.kickPlayer(plugin.getConfigManager().getMessage("anticheat-kick"));
            }
        }
    }

    public void resetViolations(UUID uuid) {
        violations.remove(uuid);
        lastLocations.remove(uuid);
        lastMoveTime.remove(uuid);
    }

    public int getViolations(UUID uuid) {
        return violations.getOrDefault(uuid, 0);
    }
}
