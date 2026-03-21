package me.crossrealmmc.realmgate;

import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.detection.PlayerDetector;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RealmGate — Nuestro propio sistema de autenticacion Bedrock.
 * Reemplaza a Floodgate sin depender de el.
 * Hecho por soyadrianyt001
 */
public class RealmGate {

    private final CrossRealmMC plugin;

    // IP -> sesion pendiente de autenticacion
    private final Map<String, BedrockSession> pendingSessions = new HashMap<>();
    // IP -> sesion autenticada
    private final Map<String, BedrockSession> authenticatedSessions = new HashMap<>();

    public RealmGate(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    public BedrockSession createSession(InetSocketAddress address, String bedrockVersion) {
        String ip = address.getAddress().getHostAddress();

        // Verificar cooldown
        if (plugin.getPlayerDetector().isOnCooldown(ip)) {
            long remaining = plugin.getPlayerDetector().getRemainingCooldown(ip);
            plugin.debugLog("Sesion rechazada por cooldown: " + ip + " (" + remaining + "s restantes)");
            return null;
        }

        // Verificar limite de jugadores Bedrock
        int maxBedrock = plugin.getConfigManager().getMaxBedrockPlayers();
        if (maxBedrock > 0 && plugin.getPlayerDetector().getOnlineBedrockCount() >= maxBedrock) {
            plugin.debugLog("Sesion rechazada: limite de Bedrock alcanzado (" + maxBedrock + ")");
            return null;
        }

        // Verificar version soportada
        if (!plugin.getConfigManager().getSupportedBedrockVersions().contains(bedrockVersion)) {
            plugin.debugLog("Version no soportada: " + bedrockVersion);
            return null;
        }

        plugin.getPlayerDetector().setCooldown(ip);

        BedrockSession session = new BedrockSession(address, bedrockVersion);
        pendingSessions.put(ip, session);
        plugin.debugLog("Sesion Bedrock creada: " + ip + " | Version: " + bedrockVersion);
        return session;
    }

    public boolean authenticateSession(String ip, String username, String xuid) {
        BedrockSession session = pendingSessions.get(ip);
        if (session == null) return false;

        // Modo online Bedrock: verificar que tiene XUID valido (Xbox)
        if (plugin.getConfigManager().isBedrockOnlineMode()) {
            if (xuid == null || xuid.isEmpty()) {
                plugin.debugLog("Autenticacion fallida (online mode): sin XUID - " + ip);
                pendingSessions.remove(ip);
                return false;
            }
        }

        // Agregar prefijo al nombre
        String prefixedName = plugin.getConfigManager().getBedrockPrefix() + username;
        session.setUsername(prefixedName);
        session.setXuid(xuid);
        session.setAuthenticated(true);

        // Generar UUID basado en nombre para modo offline, o en XUID para online
        UUID uuid;
        if (xuid != null && !xuid.isEmpty()) {
            uuid = UUID.nameUUIDFromBytes(("bedrock:" + xuid).getBytes());
        } else {
            uuid = UUID.nameUUIDFromBytes(("bedrock_offline:" + username).getBytes());
        }
        session.setUuid(uuid);

        // Registrar en detector
        plugin.getPlayerDetector().registerPlayer(uuid, PlayerDetector.PlayerType.BEDROCK, session.getBedrockVersion());

        pendingSessions.remove(ip);
        authenticatedSessions.put(ip, session);

        plugin.debugLog("Jugador Bedrock autenticado: " + prefixedName + " | UUID: " + uuid);
        return true;
    }

    public BedrockSession getSession(String ip) {
        return authenticatedSessions.getOrDefault(ip, pendingSessions.get(ip));
    }

    public void removeSession(String ip) {
        BedrockSession session = authenticatedSessions.remove(ip);
        if (session == null) session = pendingSessions.remove(ip);
        if (session != null && session.getUuid() != null) {
            plugin.getPlayerDetector().unregisterPlayer(session.getUuid());
        }
        plugin.debugLog("Sesion eliminada: " + ip);
    }

    public boolean isAuthenticated(String ip) {
        BedrockSession session = authenticatedSessions.get(ip);
        return session != null && session.isAuthenticated();
    }

    public int getActiveSessionCount() {
        return authenticatedSessions.size();
    }
}
