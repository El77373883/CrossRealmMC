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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RealmGate {

    private final CrossRealmMC plugin;
    private final ExecutorService javaConnector = Executors.newFixedThreadPool(10);

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

    public void registerAuthenticatedSession(String ip, BedrockSession session) {
        authenticatedSessions.put(ip, session);
        plugin.debugLog("Sesion directa registrada: " + ip + " | " + session.getUsername());
    }

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

        javaConnector.submit(() -> {
            Socket socket = null;
            try {
                String host = plugin.getConfigManager().getJavaServerHost();
                int port    = plugin.getConfigManager().getJavaServerPort();

                plugin.debugLog("Conectando al servidor Java: " + host + ":" + port
                        + " para " + session.getUsername());

                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(15000);

                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(socket.getOutputStream()));
                DataInputStream in = new DataInputStream(
                        new BufferedInputStream(socket.getInputStream()));

                sendJavaHandshake(out, host, port);
                sendJavaLoginStart(out, session.getUsername(), session.getUuid());
                boolean success = waitForLoginSuccess(in, out, session);

                if (success) {
                    javaConnections.put(ip, socket);
                    session.setJavaConnected(true);
                    plugin.debugLog("✔ " + session.getUsername() + " conectado al servidor Java");
                } else {
                    plugin.debugLog("✘ Servidor Java rechazo a: " + session.getUsername());
                    removeSession(ip);
                }
            } catch (Exception e) {
                plugin.debugLog("✘ Error al conectar Java para "
                        + session.getUsername() + ": " + e.getMessage());
                removeSession(ip);
            } finally {
                if (socket != null && !javaConnections.containsKey(ip)) {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }
        });
    }

    private void sendJavaHandshake(DataOutputStream out, String host, int port) throws IOException {
        ByteArrayOutputStream buf  = new ByteArrayOutputStream();
        DataOutputStream      data = new DataOutputStream(buf);
        writeVarInt(data, 0x00);
        writeVarInt(data, 771); // Protocolo Java para 1.21.11
        writeJavaString(data, host);
        data.writeShort(port);
        writeVarInt(data, 2);
        sendJavaPacket(out, buf.toByteArray());
        plugin.debugLog("Handshake Java enviado");
    }

    private void sendJavaLoginStart(DataOutputStream out, String username, UUID uuid) throws IOException {
        ByteArrayOutputStream buf  = new ByteArrayOutputStream();
        DataOutputStream      data = new DataOutputStream(buf);
        writeVarInt(data, 0x00);
        writeJavaString(data, username);
        data.writeLong(uuid.getMostSignificantBits());
        data.writeLong(uuid.getLeastSignificantBits());
        sendJavaPacket(out, buf.toByteArray());
        plugin.debugLog("LoginStart enviado: " + username);
    }

    private void sendLoginAcknowledged(DataOutputStream out) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        DataOutputStream contentData = new DataOutputStream(content);
        writeVarInt(contentData, 0x03);

        ByteArrayOutputStream wrapper = new ByteArrayOutputStream();
        DataOutputStream wrapperData = new DataOutputStream(wrapper);
        writeVarInt(wrapperData, 0);
        wrapperData.write(content.toByteArray());

        writeVarInt(out, wrapper.size());
        out.write(wrapper.toByteArray());
        out.flush();
        plugin.debugLog("LoginAcknowledged enviado");
    }

    private void sendClientSettings(DataOutputStream out, int compressionThreshold) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        DataOutputStream contentData = new DataOutputStream(content);
        writeVarInt(contentData, 0x00);
        writeJavaString(contentData, "en_US");
        contentData.writeByte(10);
        writeVarInt(contentData, 0);
        contentData.writeBoolean(true);
        contentData.writeByte(0x7F);
        writeVarInt(contentData, 1);
        contentData.writeBoolean(false);
        contentData.writeBoolean(true);
        writeVarInt(contentData, 0);
        sendCompressed(out, content.toByteArray(), compressionThreshold);
        plugin.debugLog("ClientSettings enviado");
    }

    private void sendAcknowledgeFinishConfiguration(DataOutputStream out, int compressionThreshold) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        DataOutputStream contentData = new DataOutputStream(content);
        writeVarInt(contentData, 0x03);
        sendCompressed(out, content.toByteArray(), compressionThreshold);
        plugin.debugLog("AcknowledgeFinishConfiguration enviado");
    }

    private void sendCompressed(DataOutputStream out, byte[] packetData, int threshold) throws IOException {
        if (threshold >= 0) {
            ByteArrayOutputStream wrapper = new ByteArrayOutputStream();
            DataOutputStream wrapperData = new DataOutputStream(wrapper);
            writeVarInt(wrapperData, 0);
            wrapperData.write(packetData);
            writeVarInt(out, wrapper.size());
            out.write(wrapper.toByteArray());
        } else {
            writeVarInt(out, packetData.length);
            out.write(packetData);
        }
        out.flush();
    }

    // ==================== MÉTODO CORREGIDO ====================
    private boolean waitForLoginSuccess(DataInputStream in, DataOutputStream out, BedrockSession session) throws IOException {
        long timeout = System.currentTimeMillis() + 15000;
        int compressionThreshold = -1;
        boolean inConfigState = false;

        while (System.currentTimeMillis() < timeout) {
            if (in.available() == 0) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                continue;
            }

            byte[] data;

            if (compressionThreshold >= 0) {
                int compressedLen = readVarInt(in);
                byte[] compressedData = new byte[compressedLen];
                in.readFully(compressedData);
                DataInputStream tmp = new DataInputStream(new ByteArrayInputStream(compressedData));
                int dataLength = readVarInt(tmp);
                if (dataLength == 0) {
                    int remaining = compressedLen - varIntSize(0);
                    data = new byte[remaining];
                    tmp.readFully(data);
                } else {
                    byte[] compressed = new byte[compressedLen - varIntSize(dataLength)];
                    tmp.readFully(compressed);
                    data = decompress(compressed, dataLength);
                }
            } else {
                int len = readVarInt(in);
                if (len <= 0) continue;
                data = new byte[len];
                in.readFully(data);
            }

            DataInputStream pkt = new DataInputStream(new ByteArrayInputStream(data));
            int id = readVarInt(pkt);
            plugin.debugLog("Respuesta Java: 0x" + String.format("%02X", id)
                    + (inConfigState ? " [CONFIG]" : " [LOGIN]"));

            if (!inConfigState) {
                // Estado LOGIN
                if (id == 0x02) {
                    long msb  = pkt.readLong();
                    long lsb  = pkt.readLong();
                    String name = readJavaString(pkt);
                    session.setUuid(new UUID(msb, lsb));
                    session.setUsername(name);
                    plugin.debugLog("LoginSuccess: " + name);
                    sendLoginAcknowledged(out);
                    // Enviamos ClientSettings
                    sendClientSettings(out, compressionThreshold);
                    plugin.debugLog("ClientSettings enviado");
                    // ✅ Enviamos FinishConfiguration AHORA (después de ClientSettings)
                    sendAcknowledgeFinishConfiguration(out, compressionThreshold);
                    plugin.debugLog("FinishConfiguration enviado al servidor");
                    inConfigState = true;
                } else if (id == 0x00) {
                    plugin.debugLog("Disconnect login: " + readJavaString(pkt));
                    return false;
                } else if (id == 0x03) {
                    compressionThreshold = readVarInt(pkt);
                    plugin.debugLog("SetCompression threshold: " + compressionThreshold);
                }
            } else {
                // Estado CONFIGURATION
                if (id == 0x02) {
                    // El servidor ha terminado su configuración
                    plugin.debugLog("FinishConfiguration recibido del servidor");
                    return true;
                } else if (id == 0x00) {
                    plugin.debugLog("Disconnect config: " + readJavaString(pkt));
                    return false;
                } else if (id == 0x01) {
                    plugin.debugLog("PluginMessage servidor ignorado: 0x01");
                } else if (id == 0x05) {
                    long pingId = pkt.readLong();
                    ByteArrayOutputStream pongContent = new ByteArrayOutputStream();
                    DataOutputStream pongData = new DataOutputStream(pongContent);
                    writeVarInt(pongData, 0x04);
                    pongData.writeLong(pingId);
                    sendCompressed(out, pongContent.toByteArray(), compressionThreshold);
                    plugin.debugLog("Pong config enviado");
                } else if (id == 0x0D) {
                    ByteArrayOutputStream packsContent = new ByteArrayOutputStream();
                    DataOutputStream packsData = new DataOutputStream(packsContent);
                    writeVarInt(packsData, 0x07);
                    writeVarInt(packsData, 0);
                    sendCompressed(out, packsContent.toByteArray(), compressionThreshold);
                    plugin.debugLog("KnownPacks enviado");
                } else {
                    plugin.debugLog("Config paquete ignorado: 0x" + String.format("%02X", id));
                }
            }
        }
        plugin.debugLog("Timeout esperando login/config");
        return false;
    }
    // ==================== FIN DEL MÉTODO CORREGIDO ====================

    private byte[] decompress(byte[] data, int expectedSize) throws IOException {
        try {
            java.util.zip.Inflater inflater = new java.util.zip.Inflater();
            inflater.setInput(data);
            byte[] result = new byte[expectedSize];
            inflater.inflate(result);
            inflater.end();
            return result;
        } catch (Exception e) {
            throw new IOException("Error descomprimiendo: " + e.getMessage());
        }
    }

    private int varIntSize(int value) {
        int size = 0;
        do { size++; value >>>= 7; } while (value != 0);
        return size;
    }

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
