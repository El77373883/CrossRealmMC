package me.crossrealmmc.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import me.crossrealmmc.CrossRealmMC;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class PacketTranslator {

    private final CrossRealmMC plugin;
    private final AtomicInteger sendSequence;
    private final AtomicInteger messageIndex;
    private final AtomicInteger orderIndex;

    public static final int PACKET_LOGIN                = 0x01;
    public static final int PACKET_RESOURCE_PACK_RESP   = 0x08;
    public static final int PACKET_MOVE_PLAYER          = 0x13;
    public static final int PACKET_PLAYER_ACTION        = 0x24;
    public static final int PACKET_ANIMATE              = 0x2C;
    public static final int PACKET_CHAT                 = 0x09;
    public static final int PACKET_DISCONNECT           = 0x05;
    public static final int PACKET_REQUEST_CHUNK_RADIUS = 0x45;
    public static final int PACKET_SUB_CHUNK_REQUEST    = 0x5C;

    public PacketTranslator(CrossRealmMC plugin, AtomicInteger sendSequence,
            AtomicInteger messageIndex, AtomicInteger orderIndex) {
        this.plugin = plugin;
        this.sendSequence = sendSequence;
        this.messageIndex = messageIndex;
        this.orderIndex = orderIndex;
    }

    public void handleIncoming(ByteBuf buf, InetSocketAddress sender,
            BedrockPlayer player, BedrockLoginHandler loginHandler,
            io.netty.channel.ChannelHandlerContext ctx) {

        if (!buf.isReadable()) return;

        int packetId = readVarInt(buf);
        plugin.debugLog("Paquete Bedrock: 0x" + String.format("%02X", packetId)
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
            case PACKET_SUB_CHUNK_REQUEST:
                handleSubChunkRequest(ctx, buf, sender, player, loginHandler);
                break;
            case 0xC1:
                handleRequestNetworkSettings(ctx, buf, sender, player, loginHandler);
                break;
            case 0x71:
                plugin.debugLog("SetLocalPlayerAsInitialized de: " + player.getUsername());
                break;
            case 0x81:
                plugin.debugLog("ClientCacheStatus recibido");
                break;
            default:
                plugin.debugLog("Paquete no manejado: 0x" + String.format("%02X", packetId));
                break;
        }
    }

    private void handleSubChunkRequest(io.netty.channel.ChannelHandlerContext ctx,
            ByteBuf buf, InetSocketAddress sender,
            BedrockPlayer player, BedrockLoginHandler loginHandler) {
        try {
            int dimension = readVarInt(buf);
            int baseX = buf.readIntLE();
            int baseY = buf.readIntLE();
            int baseZ = buf.readIntLE();
            int count = buf.readIntLE();

            plugin.debugLog("SubChunkRequest | dim=" + dimension
                    + " base=(" + baseX + "," + baseY + "," + baseZ + ") count=" + count);

            for (int i = 0; i < count && buf.isReadable(3); i++) {
                byte dx = buf.readByte();
                byte dy = buf.readByte();
                byte dz = buf.readByte();
                int cx = baseX + dx;
                int cy = baseY + dy;
                int cz = baseZ + dz;
                loginHandler.sendSubChunkResponse(ctx, sender, dimension, cx, cy, cz);
            }
        } catch (Exception e) {
            plugin.debugLog("Error SubChunkRequest: " + e.getMessage());
        }
    }

    private void handleRequestNetworkSettings(
            io.netty.channel.ChannelHandlerContext ctx,
            ByteBuf buf, InetSocketAddress sender,
            BedrockPlayer player, BedrockLoginHandler loginHandler) {
        try {
            int protocol = buf.readInt();
            plugin.debugLog("RequestNetworkSettings | Protocolo: " + protocol);

            ByteBuf payload = Unpooled.buffer();
            BedrockLoginHandler.writeVarInt(payload, 0x8F);
            payload.writeShortLE(0);
            payload.writeShortLE(0xFF);
            payload.writeBoolean(false);
            payload.writeByte(0);
            payload.writeFloatLE(0);

            loginHandler.sendRawGamePacketPublic(ctx, sender, payload);

            player.setState(BedrockPlayer.State.LOGIN);
            plugin.debugLog("NetworkSettings enviado | estado → LOGIN");
        } catch (Exception e) {
            plugin.debugLog("Error NetworkSettings: " + e.getMessage());
        }
    }

    private void handleMovePlayer(ByteBuf buf, BedrockPlayer player) {
        if (!buf.isReadable(28)) return;
        try {
            readVarLong(buf);
            float x     = buf.readFloatLE();
            float y     = buf.readFloatLE();
            float z     = buf.readFloatLE();
            float yaw   = buf.readFloatLE();
            float pitch = buf.readFloatLE();
            player.setPosition(x, y, z);
            player.setRotation(yaw, pitch);
        } catch (Exception ignored) {}
    }

    private void handleChat(ByteBuf buf, BedrockPlayer player) {
        if (player.getUsername() == null) return;
        try {
            readString(buf);
            readString(buf);
            String msg = readString(buf);
            if (msg != null && !msg.isEmpty()) {
                String prefix = plugin.getConfigManager().getBedrockChatPrefix();
                final String finalMsg = msg;
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    org.bukkit.Bukkit.broadcastMessage(prefix + " §f" + player.getUsername() + "§7: §f" + finalMsg)
                );
            }
        } catch (Exception ignored) {}
    }

    private void handleAnimate(ByteBuf buf, BedrockPlayer player) {
        plugin.debugLog("Animate de: " + player.getUsername());
    }

    private void handleChunkRadius(io.netty.channel.ChannelHandlerContext ctx,
            ByteBuf buf, InetSocketAddress sender, BedrockPlayer player,
            BedrockLoginHandler loginHandler) {
        int radius = readVarInt(buf);
        plugin.debugLog("ChunkRadius request de: " + player.getUsername() + " radio: " + radius);

        ByteBuf reply = Unpooled.buffer();
        BedrockLoginHandler.writeVarInt(reply, 0x46);
        BedrockLoginHandler.writeVarInt(reply, 4);
        loginHandler.sendGamePacketPublic(ctx, sender, reply);
        plugin.debugLog("ChunkRadiusReply respondido");

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
