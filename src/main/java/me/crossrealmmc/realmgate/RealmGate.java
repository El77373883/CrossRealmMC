package me.crossrealmmc.realmgate;

import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.bedrock.BedrockPlayer;
import me.crossrealmmc.detection.PlayerDetector;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RealmGate {

    private final CrossRealmMC plugin;

    private final Map<String, BedrockSession> pendingSessions       = new HashMap<>();
    private final Map<String, BedrockSession> authenticatedSessions = new HashMap<>();
    private final Map<String, Socket>         javaConnections       = new HashMap<>();

    public RealmGate(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    public BedrockSession createSession(InetSocketAddress address, String bedrockVersion) {
        String ip = address.getAddress().getHostAddress();

        if (plugin.getPlayerDetector().isOnCooldown(ip)) {
            long remaining = plugin.getPlayerDetector().getRemainingCooldown(ip);
            plugin.debugLog("Sesion rechazada por cooldown: " + ip + " (" + remaining + "s restantes)");
            return null;
        }

        int maxBedrock = plugin.getConfigManager().getMaxBedrockPlayers();
        if (maxBedrock > 0 && plugin.getPlayerDetector().getOnlineBedrockCount() >= maxBedrock) {
            plugin.debugLog("Sesion rechazada: limite de Bedrock alcanzado (" + maxBedrock + ")");
            return null;
        }

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

        if (plugin.getConfigManager().isBedrockOnlineMode()) {
            if (xuid == null || xuid.isEmpty()) {
                plugin.debugLog("Autenticacion fallida (online mode): sin XUID - " + ip);
                pendingSessions.remove(ip);
                return false;
            }
        }

        String prefixedName = plugin.getConfigManager().getBedrockPrefix() + username;
        session.setUsername(prefixedName);
        session.setXuid(xuid);
        session.setAuthenticated(true);

        UUID uuid;
        if (xuid != null && !xuid.isEmpty()) {
            uuid = UUID.nameUUIDFromBytes(("bedrock:" + xuid).getBytes());
        } else {
            uuid = UUID.nameUUIDFromBytes(("bedrock_offline:" + username).getBytes());
        }
        session.setUuid(uuid);

        plugin.getPlayerDetector().registerPlayer(uuid, PlayerDetector.PlayerType.BEDROCK, session.getBedrockVersion());

        pendingSessions.remove(ip);
        authenticatedSessions.put(ip, session);

        plugin.debugLog("Jugador Bedrock autenticado: " + prefixedName + " | UUID: " + uuid);
        return true;
    }

    /**
     * Llamado desde BedrockLoginHandler después del spawn.
     * Conecta al servidor Java en un hilo separado.
     */
    public void connectToJavaAfterSpawn(String ip, BedrockPlayer player) {
        BedrockSession session = authenticatedSessions.get(ip);
        if (session == null) {
            plugin.debugLog("Sin sesion para conectar al Java: " + ip);
            return;
        }
        if (session.isJavaConnected()) {
            plugin.debugLog("Ya conectado al Java: " + ip);
            return;
        }

        new Thread(() -> {
            try {
                String host = plugin.getConfigManager().getJavaServerHost();
                int port    = plugin.getConfigManager().getJavaServerPort();

                plugin.debugLog("Conectando al servidor Java: " + host + ":" + port
                        + " para " + session.getUsername());

                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(10000);

                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(socket.getOutputStream()));
                DataInputStream  in  = new DataInputStream(
                        new BufferedInputStream(socket.getInputStream()));

                // --- Handshake ---
                sendJavaHandshake(out, host, port);

                // --- LoginStart ---
                sendJavaLoginStart(out, session.getUsername(), session.getUuid());

                // --- Esperar LoginSuccess ---
                boolean success = waitForLoginSuccess(in, session);

                if (success) {
                    javaConnections.put(ip, socket);
                    session.setJavaConnected(true);
                    plugin.debugLog("✔ " + session.getUsername() + " conectado al servidor Java");
                } else {
                    socket.close();
                    plugin.debugLog("✘ Servidor Java rechazó a: " + session.getUsername());
                    removeSession(ip);
                }

            } catch (Exception e) {
                plugin.debugLog("✘ Error al conectar Java para "
                        + session.getUsername() + ": " + e.getMessage());
                removeSession(ip);
            }
        }, "JavaConnect-" + session.getUsername()).start();
    }

    // ── Handshake Java (protocolo 769 = 1.21.4) ──────────────────────────────

    private void sendJavaHandshake(DataOutputStream out, String host, int port) throws IOException {
        ByteArrayOutputStream buf  = new ByteArrayOutputStream();
        DataOutputStream      data = new DataOutputStream(buf);
        writeVarInt(data, 0x00);   // Packet ID
        writeVarInt(data, 769);    // Protocol version 1.21.4 — cambia si tu server es diferente
        writeJavaString(data, host);
        data.writeShort(port);
        writeVarInt(data, 2);      // Next state: Login
        sendJavaPacket(out, buf.toByteArray());
        plugin.debugLog("Handshake Java enviado");
    }

    private void sendJavaLoginStart(DataOutputStream out, String username, UUID uuid) throws IOException {
        ByteArrayOutputStream buf  = new ByteArrayOutputStream();
        DataOutputStream      data = new DataOutputStream(buf);
        writeVarInt(data, 0x00);   // Packet ID: LoginStart
        writeJavaString(data, username);
        data.writeBoolean(true);   // Has UUID
        data.writeLong(uuid.getMostSignificantBits());
        data.writeLong(uuid.getLeastSignificantBits());
        sendJavaPacket(out, buf.toByteArray());
        plugin.debugLog("LoginStart enviado: " + username);
    }

    private boolean waitForLoginSuccess(DataInputStream in, BedrockSession session) throws IOException {
        long timeout = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < timeout) {
            if (in.available() == 0) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                continue;
            }
            int len = readVarInt(in);
            if (len <= 0) continue;
            byte[] data = new byte[len];
            in.readFully(data);
            DataInputStream pkt = new DataInputStream(new ByteArrayInputStream(data));
            int id = readVarInt(pkt);
            plugin.debugLog("Respuesta Java: 0x" + String.format("%02X", id));

            if (id == 0x02) { // LoginSuccess
                long msb  = pkt.readLong();
                long lsb  = pkt.readLong();
                String name = readJavaString(pkt);
                session.setUuid(new UUID(msb, lsb));
                session.setUsername(name);
                plugin.debugLog("LoginSuccess: " + name);
                return true;
            } else if (id == 0x00) { // Disconnect
                plugin.debugLog("Disconnect del servidor Java: " + readJavaString(pkt));
                return false;
            } else if (id == 0x03) { // SetCompression — ignorar por ahora
                plugin.debugLog("SetCompression recibido, ignorando");
            }
        }
        plugin.debugLog("Timeout esperando LoginSuccess");
        return false;
    }

    // ── Utilidades protocolo Java ─────────────────────────────────────────────

    private void sendJavaPacket(DataOutputStream out, byte[] data) throws IOException {
        writeVarInt(out, data.length);
        out.write(data);
        out.flush();
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) { out.writeByte(value); return; }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int value = 0, pos = 0;
        byte b;
        do {
            b = in.readByte();
            value |= (b & 0x7F) << pos;
            pos += 7;
            if (pos >= 32) throw new IOException("VarInt muy grande");
        } while ((b & 0x80) != 0);
        return value;
    }

    private void writeJavaString(DataOutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private String readJavaString(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ── Sesiones ──────────────────────────────────────────────────────────────

    public BedrockSession getSession(String ip) {
        return authenticatedSessions.getOrDefault(ip, pendingSessions.get(ip));
    }

    public void removeSession(String ip) {
        BedrockSession session = authenticatedSessions.remove(ip);
        if (session == null) session = pendingSessions.remove(ip);
        if (session != null && session.getUuid() != null) {
            plugin.getPlayerDetector().unregisterPlayer(session.getUuid());
        }
        Socket sock = javaConnections.remove(ip);
        if (sock != null) try { sock.close(); } catch (IOException ignored) {}
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
