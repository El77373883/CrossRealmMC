package me.crossrealmmc.bedrock;

import io.netty.buffer.ByteBuf;
import me.crossrealmmc.CrossRealmMC;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * PacketTranslator — Traduce paquetes Bedrock → Java y Java → Bedrock
 * Hecho por soyadrianyt001
 */
public class PacketTranslator {

    private final CrossRealmMC plugin;

    // Paquetes Bedrock entrantes del cliente
    public static final int PACKET_LOGIN                = 0x01;
    public static final int PACKET_RESOURCE_PACK_RESP   = 0x08;
    public static final int PACKET_MOVE_PLAYER          = 0x13;
    public static final int PACKET_PLAYER_ACTION        = 0x24;
    public static final int PACKET_ANIMATE              = 0x2C;
    public static final int PACKET_CHAT                 = 0x09;
    public static final int PACKET_DISCONNECT           = 0x05;
    public static final int PACKET_REQUEST_CHUNK_RADIUS = 0x45;

    public PacketTranslator(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Traduce y maneja un paquete recibido del cliente Bedrock
     */
    public void handleIncoming(ByteBuf buf, InetSocketAddress sender,
            BedrockPlayer player, BedrockLoginHandler loginHandler,
            io.netty.channel.ChannelHandlerContext ctx) {

        if (!buf.isReadable()) return;

        int packetId = readVarInt(buf);
        plugin.debugLog("Paquete Bedrock entrante: 0x" + String.format("%02X", packetId)
                + " | Estado: " + player.getState()
                + " | Jugador: " + (player.getUsername() != null ? player.getUsername() : "unknown"));

        switch (packetId) {
            case PACKET_LOGIN:
                loginHandler.handleLoginPacket(ctx, buf, sender, player);
                break;

            case PACKET_RESOURCE_PACK_RESP:
                loginHandler.handleResourcePackResponse(ctx, buf, sender, player);
                break;

            case PACKET_MOVE_PLAYER:
                handleMovePlayer(buf, player);
                break;

            case PACKET_CHAT:
                handleChat(buf, player);
                break;

            case PACKET_ANIMATE:
                handleAnimate(buf, player);
                break;

            case PACKET_REQUEST_CHUNK_RADIUS:
                handleChunkRadius(ctx, buf, sender, player, loginHandler);
                break;

            case PACKET_DISCONNECT:
                handleDisconnect(sender, player);
                break;

            case PACKET_PLAYER_ACTION:
                handlePlayerAction(buf, player);
                break;

            default:
                plugin.debugLog("Paquete no manejado: 0x" + String.format("%02X", packetId));
                break;
        }
    }

    // ─────────────────────────────────────────
    // HANDLERS ENTRANTES
    // ─────────────────────────────────────────

    private void handleMovePlayer(ByteBuf buf, BedrockPlayer player) {
        if (!buf.isReadable(28)) return;
        try {
            long runtimeId = readVarLong(buf);
            float x   = buf.readFloatLE();
            float y   = buf.readFloatLE();
            float z   = buf.readFloatLE();
            float yaw = buf.readFloatLE();
            float pitch = buf.readFloatLE();
            player.setPosition(x, y, z);
            player.setRotation(yaw, pitch);
            plugin.debugLog("Move: " + player.getUsername() + " → " + x + "," + y + "," + z);
        } catch (Exception ignored) {}
    }

    private void handleChat(ByteBuf buf, BedrockPlayer player) {
        if (player.getUsername() == null) return;
        try {
            String type = readString(buf);
            String source = readString(buf);
            String msg = readString(buf);

            if (msg != null && !msg.isEmpty()) {
                String prefix = plugin.getConfigManager().getBedrockChatPrefix();
                final String finalMsg = msg;
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    org.bukkit.Bukkit.broadcastMessage(prefix + " §f" + player.getUsername() + "§7: §f" + finalMsg)
                );
                plugin.debugLog("Chat Bedrock: " + player.getUsername() + ": " + msg);
            }
        } catch (Exception ignored) {}
    }

    private void handleAnimate(ByteBuf buf, BedrockPlayer player) {
        plugin.debugLog("Animate de: " + player.getUsername());
    }

    private void handleChunkRadius(io.netty.channel.ChannelHandlerContext ctx,
            ByteBuf buf, InetSocketAddress sender, BedrockPlayer player,
            BedrockLoginHandler loginHandler) {
        plugin.debugLog("ChunkRadius request de: " + player.getUsername());
        // Si estamos en spawning y recibimos chunk radius, ya podemos hacer start game
        if (player.getState() == BedrockPlayer.State.LOGIN) {
            loginHandler.sendStartGame(ctx, sender, player);
        }
    }

    private void handleDisconnect(InetSocketAddress sender, BedrockPlayer player) {
        plugin.debugLog("Desconexion de: " + (player.getUsername() != null ? player.getUsername() : sender.toString()));
        if (player.getUuid() != null) {
            plugin.getPlayerDetector().unregisterPlayer(player.getUuid());
            final String name = player.getUsername();
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                String leaveMsg = plugin.getConfigManager().getMessage("player-leave", "{player}", name);
                org.bukkit.Bukkit.broadcastMessage(leaveMsg);
            });
        }
    }

    private void handlePlayerAction(ByteBuf buf, BedrockPlayer player) {
        plugin.debugLog("PlayerAction de: " + player.getUsername());
    }

    // ─────────────────────────────────────────
    // UTILS
    // ─────────────────────────────────────────

    public static int readVarInt(ByteBuf buf) {
        int value = 0;
        int size = 0;
        int b;
        while (((b = buf.readByte()) & 0x80) == 0x80) {
            value |= (b & 0x7F) << (size++ * 7);
            if (size > 5) return value;
        }
        return value | ((b & 0x7F) << (size * 7));
    }

    public static long readVarLong(ByteBuf buf) {
        long value = 0;
        int size = 0;
        int b;
        while (((b = buf.readByte()) & 0x80) == 0x80) {
            value |= (long)(b & 0x7F) << (size++ * 7);
            if (size > 10) return value;
        }
        return value | ((long)(b & 0x7F) << (size * 7));
    }

    public static String readString(ByteBuf buf) {
        int length = readVarInt(buf);
        if (length <= 0 || length > 32767) return "";
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
