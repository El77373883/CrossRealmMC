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
            case 0xC1:
                handleRequestNetworkSettings(ctx, buf, sender, player, loginHandler);
                break;
            default:
                plugin.debugLog("Paquete no manejado: 0x" + String.format("%02X", packetId));
                break;
        }
    }

    private void handleRequestNetworkSettings(
            io.netty.channel.ChannelHandlerContext ctx,
            ByteBuf buf, InetSocketAddress sender,
            BedrockPlayer player, BedrockLoginHandler loginHandler) {
        try {
            int protocol = buf.readInt();
            plugin.debugLog("RequestNetworkSettings | Protocolo: " + protocol);

            // NetworkSettings — solo threshold y algorithm
            ByteBuf payload = Unpooled.buffer();
            BedrockLoginHandler.writeVarInt(payload, 0x0F);
            payload.writeShortLE(0);   // compression_threshold
            payload.writeShortLE(0);   // compression_algorithm = zlib

            // Envolver en 0xFE
            ByteBuf gamePacket = Unpooled.buffer();
            gamePacket.writeByte(0xFE);
            BedrockLoginHandler.writeVarInt(gamePacket, payload.readableBytes());
            gamePacket.writeBytes(payload);
            payload.release();

            // Reliable ordered
            ByteBuf frame = Unpooled.buffer();
            frame.writeByte(0x84);
            frame.writeMediumLE(sendSequence.getAndIncrement());
            frame.writeByte(0x60);
            frame.writeShort(gamePacket.readableBytes() * 8);
            frame.writeMediumLE(messageIndex.getAndIncrement());
            frame.writeMediumLE(orderIndex.getAndIncrement());
            frame.writeByte(0);
            frame.writeBytes(gamePacket);
            gamePacket.release();

            ctx.writeAndFlush(new DatagramPacket(frame, sender));
            plugin.debugLog("NetworkSettings enviado | simple");
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
        plugin.debugLog("ChunkRadius request de: " + player.getUsername());
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
